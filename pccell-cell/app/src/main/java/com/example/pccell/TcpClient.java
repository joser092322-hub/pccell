package com.example.pccell;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpClient {
    public interface ClientListener {
        void onStatusChanged(String status, boolean connected);
        void onTextReceived(String text);
        default void onClipboardReceived(String text) {}
        default void onCommandReceived(String cmd, String param) {}
        default void onDirDataReceived(String currentPath, List<RemoteDirItem> items) {}
        default void onHardwareStatsReceived(int cpu, int ram, long ramFreeMb, long ramTotalMb, int disk, long diskFreeGb, long diskTotalGb) {}
        default void onScreenshotReceived(byte[] jpegBytes) {}
        default void onRemoteScreenFrameReceived(byte[] frameData) {}
        OutputStream createFileOutputStream(String name) throws IOException;
        void onFileReceived(String name, long size);
        void onFileSent(String name, long size);
        void onProgress(String description, int percent);
        void onError(String error);
    }

    private static final int BUFFER_SIZE = 64 * 1024;
    private final ClientListener listener;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private volatile Socket socket;
    private DataOutputStream out;
    private volatile boolean connected;
    private volatile boolean connecting;

    public TcpClient(ClientListener listener) { this.listener = listener; }
    public boolean isConectado() { return connected; }
    public boolean isConectando() { return connecting; }

    public void connect(String host, int port) {
        synchronized (lock) { if (connected || connecting) return; connecting = true; }
        new Thread(() -> {
            listener.onStatusChanged("Conectando a " + host + ":" + port + "...", false);
            Socket s = new Socket(); socket = s;
            try {
                s.connect(new InetSocketAddress(host, port), 5000);
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                synchronized (lock) {
                    if (!connecting) { s.close(); return; }
                    out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                    connected = true; connecting = false;
                }
                listener.onStatusChanged("Conectado ao PC (" + host + ")", true);
                while (connected && !s.isClosed()) {
                    String type = in.readUTF();
                    if ("TEXT".equals(type)) {
                        listener.onTextReceived(in.readUTF());
                    } else if ("FILE".equals(type)) {
                        receiveFile(in);
                    } else if ("CLIPBOARD".equals(type)) {
                        listener.onClipboardReceived(in.readUTF());
                    } else if ("CMD".equals(type)) {
                        String cmd = in.readUTF();
                        String param = in.readUTF();
                        listener.onCommandReceived(cmd, param);
                    } else if ("DIR_DATA".equals(type)) {
                        String currentPath = in.readUTF();
                        int count = in.readInt();
                        List<RemoteDirItem> items = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            boolean isDir = in.readBoolean();
                            String name = in.readUTF();
                            String fullPath = in.readUTF();
                            long size = in.readLong();
                            items.add(new RemoteDirItem(isDir, name, fullPath, size));
                        }
                        listener.onDirDataReceived(currentPath, items);
                    } else if ("SYS_STATS".equals(type)) {
                        int cpu = in.readInt();
                        int ram = in.readInt();
                        long ramFree = in.readLong();
                        long ramTotal = in.readLong();
                        int disk = in.readInt();
                        long diskFree = in.readLong();
                        long diskTotal = in.readLong();
                        listener.onHardwareStatsReceived(cpu, ram, ramFree, ramTotal, disk, diskFree, diskTotal);
                    } else if ("SCREENSHOT_DATA".equals(type)) {
                        int len = in.readInt();
                        byte[] sc = new byte[len];
                        in.readFully(sc);
                        listener.onScreenshotReceived(sc);
                    } else if ("SCREEN_FRAME".equals(type)) {
                        int len = in.readInt();
                        byte[] frameData = new byte[len];
                        in.readFully(frameData);
                        listener.onRemoteScreenFrameReceived(frameData);
                    } else {
                        throw new IOException("Tipo de mensagem desconhecido: " + type);
                    }
                }
            } catch (IOException e) {
                if (connecting) listener.onError("Não foi possível conectar: " + e.getMessage());
            } finally {
                boolean notify = connected || connecting;
                connected = false; connecting = false;
                synchronized (lock) { out = null; }
                close(s); socket = null;
                if (notify) listener.onStatusChanged("Desconectado", false);
            }
        }, "TCP-Client-Thread").start();
    }

    private void receiveFile(DataInputStream in) throws IOException {
        String name = in.readUTF();
        long size = in.readLong();
        if (size < 0) throw new IOException("Tamanho de arquivo inválido.");
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        listener.onProgress("Recebendo " + name, 0);
        try (OutputStream file = listener.createFileOutputStream(name)) {
            while (remaining > 0) {
                int count = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (count < 0) throw new EOFException("Conexão interrompida durante o arquivo.");
                file.write(buffer, 0, count);
                remaining -= count;
                int percent = size > 0 ? (int)((size - remaining) * 100 / size) : 100;
                listener.onProgress("Recebendo " + name, percent);
            }
        }
        listener.onProgress("", 100);
        listener.onFileReceived(name, size);
    }

    public void sendText(String text) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("TEXT");
            out.writeUTF(text);
            out.flush();
        }));
    }

    public void sendClipboard(String text) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("CLIPBOARD");
            out.writeUTF(text);
            out.flush();
        }));
    }

    public void sendClipboardImage(byte[] jpegBytes) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("CLIPBOARD_IMAGE");
            out.writeInt(jpegBytes.length);
            out.write(jpegBytes);
            out.flush();
        }));
    }

    public void sendMicAudio(byte[] pcmBytes) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("MIC_AUDIO");
            out.writeInt(pcmBytes.length);
            out.write(pcmBytes);
            out.flush();
        }));
    }

    public void sendCommand(String cmd, String param) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("CMD");
            out.writeUTF(cmd);
            out.writeUTF(param != null ? param : "");
            out.flush();
        }));
    }

    public void sendCameraFrame(byte[] jpegBytes) {
        sendExecutor.submit(() -> write(() -> {
            out.writeUTF("CAM_FRAME");
            out.writeInt(jpegBytes.length);
            out.write(jpegBytes);
            out.flush();
        }));
    }

    public void sendRemoteMouseMove(float normX, float normY, int screenW, int screenH) {
        sendCommand("REMOTE_MOUSE_MOVE", (int)(normX) + ":" + (int)(normY) + ":" + screenW + ":" + screenH);
    }
    public void sendRemoteMouseDown(float normX, float normY, int screenW, int screenH, String btn) {
        sendCommand("REMOTE_MOUSE_DOWN", (int)(normX) + ":" + (int)(normY) + ":" + screenW + ":" + screenH + ":" + btn);
    }
    public void sendRemoteMouseUp(float normX, float normY, int screenW, int screenH, String btn) {
        sendCommand("REMOTE_MOUSE_UP", (int)(normX) + ":" + (int)(normY) + ":" + screenW + ":" + screenH + ":" + btn);
    }
    public void sendRemoteMouseClick(float normX, float normY, int screenW, int screenH, String btn) {
        sendCommand("REMOTE_MOUSE_CLICK", (int)(normX) + ":" + (int)(normY) + ":" + screenW + ":" + screenH + ":" + btn);
    }

    public void sendFile(String name, long size, InputStream stream) {
        sendExecutor.submit(() -> {
            if (!connected || out == null) {
                close(stream);
                listener.onError("Não conectado ao PC.");
                return;
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            long sent = 0;
            try (InputStream input = new BufferedInputStream(stream)) {
                synchronized (lock) {
                    out.writeUTF("FILE");
                    out.writeUTF(name);
                    out.writeLong(size);
                    int count;
                    while (sent < size && (count = input.read(buffer, 0, (int)Math.min(buffer.length, size - sent))) != -1) {
                        out.write(buffer, 0, count);
                        sent += count;
                        int percent = size > 0 ? (int)(sent * 100 / size) : 100;
                        listener.onProgress("Enviando " + name, percent);
                    }
                    if (sent != size) throw new EOFException("O arquivo mudou durante a leitura.");
                    out.flush();
                }
                listener.onProgress("", 100);
                listener.onFileSent(name, sent);
            } catch (IOException e) {
                listener.onProgress("", 100);
                listener.onError("Falha ao enviar " + name + ": " + e.getMessage());
            }
        });
    }

    private void write(IoAction action) {
        synchronized (lock) {
            if (!connected || out == null) {
                return;
            }
            try {
                action.run();
            } catch (IOException e) {
                listener.onError("Falha na conexão: " + e.getMessage());
            }
        }
    }

    private interface IoAction { void run() throws IOException; }

    public void disconnect() {
        connected = false;
        connecting = false;
        close(socket);
        listener.onStatusChanged("Desconectado", false);
    }

    public void shutdown() {
        disconnect();
        sendExecutor.shutdownNow();
    }

    private static void close(Closeable c) {
        try { if (c != null) c.close(); } catch (IOException ignored) {}
    }
}
