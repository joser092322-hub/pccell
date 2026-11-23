import java.io.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TcpServer {

    public interface ServerListener {
        void onStatusChanged(String status, boolean conectado);
        void onTextReceived(String mensagem);
        default void onClipboardReceived(String clipboardText) {}
        default void onCommandReceived(String comando, String parametro) {}
        default void onImagePastedToClipboard() {}
        default void onFileReceived(File arquivo) {}
        default void onFileSent(File arquivo) {}
        default void onProgress(String descricao, int porcentagem) {}
        default void onCameraFrameReceived(byte[] frameData) {}
        void onError(String erro);
    }

    public static class DirItem {
        public final boolean isDir;
        public final String name;
        public final String fullPath;
        public final long size;

        public DirItem(boolean isDir, String name, String fullPath, long size) {
            this.isDir = isDir;
            this.name = name;
            this.fullPath = fullPath;
            this.size = size;
        }
    }

    private final int porta;
    private final ServerListener listener;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();
    private final Object outLock = new Object();
    private static final int BUFFER_SIZE = 64 * 1024;
    private final File pastaDownloads = new File(System.getProperty("user.home"), "Downloads/TransferApp");

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream out;
    private volatile boolean rodando = false;
    private DiscoveryServer discoveryServer;

    private volatile boolean streamingScreen = false;
    private Thread screenStreamThread = null;

    public TcpServer(int porta, ServerListener listener) {
        this.porta = porta;
        this.listener = listener;
        if (!pastaDownloads.exists()) {
            pastaDownloads.mkdirs();
        }
    }

    public File getPastaDownloads() { return pastaDownloads; }

    public void start() {
        rodando = true;
        Thread serverThread = new Thread(this::executarServidor, "TCP-Server-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        discoveryServer = new DiscoveryServer(porta);
        discoveryServer.start();
    }

    private void executarServidor() {
        try (ServerSocket ss = new ServerSocket(porta)) {
            this.serverSocket = ss;

            while (rodando) {
                listener.onStatusChanged("Aguardando conexão na porta " + porta + "...", false);
                Socket socket = ss.accept();
                this.clientSocket = socket;

                String ipCliente = socket.getInetAddress().getHostAddress();
                listener.onStatusChanged("Conectado a " + ipCliente, true);

                lidarComCliente(socket);
            }
        } catch (IOException e) {
            if (rodando) {
                listener.onError("Erro no servidor: " + e.getMessage());
            }
        }
    }

    private void lidarComCliente(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                DataOutputStream output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))
        ) {
            synchronized (outLock) {
                this.out = output;
            }

            while (rodando && !socket.isClosed()) {
                String tipo = in.readUTF();

                if ("TEXT".equals(tipo)) {
                    String mensagem = in.readUTF();
                    listener.onTextReceived(mensagem);
                } else if ("FILE".equals(tipo)) {
                    receiveFile(in);
                } else if ("CLIPBOARD".equals(tipo)) {
                    String clip = in.readUTF();
                    listener.onClipboardReceived(clip);
                } else if ("CLIPBOARD_IMAGE".equals(tipo)) {
                    int len = in.readInt();
                    byte[] data = new byte[len];
                    in.readFully(data);
                    RemoteInputController.copyImageToClipboard(data);
                    listener.onImagePastedToClipboard();
                } else if ("MIC_AUDIO".equals(tipo)) {
                    int len = in.readInt();
                    byte[] data = new byte[len];
                    in.readFully(data);
                    AudioPlaybackManager.playAudio(data);
                } else if ("CMD".equals(tipo)) {
                    String cmd = in.readUTF();
                    String param = in.readUTF();
                    handleCommand(cmd, param);
                    listener.onCommandReceived(cmd, param);
                } else if ("CAM_FRAME".equals(tipo)) {
                    int len = in.readInt();
                    byte[] frameData = new byte[len];
                    in.readFully(frameData);
                    listener.onCameraFrameReceived(frameData);
                }
            }
        } catch (EOFException e) {
            listener.onStatusChanged("Cliente desconectou.", false);
        } catch (IOException e) {
            if (rodando) {
                listener.onStatusChanged("Conexão encerrada.", false);
            }
        } finally {
            synchronized (outLock) {
                this.out = null;
            }
            stopScreenStream();
            AudioPlaybackManager.stop();
            fecharSocketCliente();
        }
    }

    private void handleCommand(String cmd, String param) {
        try {
            switch (cmd) {
                case "MOUSE_MOVE":
                    String[] coords = param.split(":");
                    RemoteInputController.moveMouse(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]));
                    break;
                case "MOUSE_CLICK":
                    RemoteInputController.clickMouse(param);
                    break;
                case "MOUSE_SCROLL":
                    RemoteInputController.mouseScroll(Integer.parseInt(param));
                    break;
                case "KEY_PRESS":
                    RemoteInputController.pressSpecialKey(param);
                    break;
                case "KEY_TYPE":
                    RemoteInputController.typeText(param);
                    break;
                case "GAMEPAD_DOWN":
                    RemoteInputController.handleGamepadButton(param, true);
                    break;
                case "GAMEPAD_UP":
                    RemoteInputController.handleGamepadButton(param, false);
                    break;
                case "MACRO":
                    RemoteInputController.executeMacro(param);
                    break;
                case "POWER":
                    String[] p = param.split(":", 2);
                    RemoteInputController.executePowerCommand(p[0], p.length > 1 ? p[1] : "");
                    break;
                case "GET_HARDWARE_STATS":
                    HardwareMonitor.SystemStats stats = HardwareMonitor.getStats();
                    sendHardwareStats(stats);
                    break;
                case "GET_SCREENSHOT":
                    byte[] sc = RemoteInputController.captureScreenshot();
                    if (sc != null) {
                        sendScreenshotData(sc);
                    }
                    break;
                case "STREAM_SCREEN_START":
                    startScreenStream();
                    break;
                case "STREAM_SCREEN_STOP":
                    stopScreenStream();
                    break;
                case "REMOTE_MOUSE_MOVE": {
                    String[] mm = param.split(":");
                    RemoteInputController.mouseMoveAbsolute(
                        Integer.parseInt(mm[0]), Integer.parseInt(mm[1]),
                        Integer.parseInt(mm[2]), Integer.parseInt(mm[3]));
                    break;
                }
                case "REMOTE_MOUSE_DOWN": {
                    String[] md = param.split(":");
                    RemoteInputController.mouseDownAbsolute(
                        Integer.parseInt(md[0]), Integer.parseInt(md[1]),
                        Integer.parseInt(md[2]), Integer.parseInt(md[3]), md[4]);
                    break;
                }
                case "REMOTE_MOUSE_UP": {
                    String[] mu = param.split(":");
                    RemoteInputController.mouseUpAbsolute(
                        Integer.parseInt(mu[0]), Integer.parseInt(mu[1]),
                        Integer.parseInt(mu[2]), Integer.parseInt(mu[3]), mu[4]);
                    break;
                }
                case "REMOTE_MOUSE_CLICK": {
                    String[] mc = param.split(":");
                    RemoteInputController.mouseClickAbsolute(
                        Integer.parseInt(mc[0]), Integer.parseInt(mc[1]),
                        Integer.parseInt(mc[2]), Integer.parseInt(mc[3]), mc[4]);
                    break;
                }
                case "BROWSE_DIR":
                    sendDirData(param);
                    break;
                case "REQUEST_FILE":
                    File target = new File(param);
                    if (target.exists() && target.isFile()) {
                        sendFile(target);
                    } else {
                        listener.onError("Arquivo remoto não encontrado: " + param);
                    }
                    break;
                default:
                    MediaController.executeCommand(cmd, param);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Erro ao processar comando " + cmd + ": " + e.getMessage());
        }
    }

    public void sendHardwareStats(HardwareMonitor.SystemStats s) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) return;
                try {
                    out.writeUTF("SYS_STATS");
                    out.writeInt(s.cpuPercent);
                    out.writeInt(s.ramPercent);
                    out.writeLong(s.ramFreeMb);
                    out.writeLong(s.ramTotalMb);
                    out.writeInt(s.diskPercent);
                    out.writeLong(s.diskFreeGb);
                    out.writeLong(s.diskTotalGb);
                    out.flush();
                } catch (IOException ignored) {}
            }
        });
    }

    public void sendScreenshotData(byte[] jpegBytes) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) return;
                try {
                    out.writeUTF("SCREENSHOT_DATA");
                    out.writeInt(jpegBytes.length);
                    out.write(jpegBytes);
                    out.flush();
                } catch (IOException ignored) {}
            }
        });
    }

    private void startScreenStream() {
        if (streamingScreen) return;
        streamingScreen = true;
        screenStreamThread = new Thread(() -> {
            while (streamingScreen) {
                try {
                    long t0 = System.currentTimeMillis();
                    byte[] frame = RemoteInputController.captureScreenFrame(960, 540);
                    if (frame != null) {
                        synchronized (outLock) {
                            if (out != null) {
                                out.writeUTF("SCREEN_FRAME");
                                out.writeInt(frame.length);
                                out.write(frame);
                                out.flush();
                            }
                        }
                    }
                    long elapsed = System.currentTimeMillis() - t0;
                    long sleepMs = Math.max(1, 16 - elapsed); // 16ms = ~60 FPS
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    break;
                }
            }
            streamingScreen = false;
        }, "Screen-Stream-Thread");
        screenStreamThread.setDaemon(true);
        screenStreamThread.start();
    }

    private void stopScreenStream() {
        streamingScreen = false;
        if (screenStreamThread != null) {
            screenStreamThread.interrupt();
            screenStreamThread = null;
        }
    }

    private void receiveFile(DataInputStream in) throws IOException {
        String name = new File(in.readUTF()).getName().replaceAll("[\\\\/:*?\"<>|]", "_");
        long size = in.readLong();
        if (size < 0) throw new IOException("Tamanho de arquivo inválido.");
        File destination = new File(pastaDownloads, name);
        String base = name;
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot) : "";
        if (dot > 0) base = name.substring(0, dot);
        for (int n = 1; destination.exists(); n++) destination = new File(pastaDownloads, base + " (" + n + ")" + extension);
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        try (FileOutputStream file = new FileOutputStream(destination)) {
            while (remaining > 0) {
                int count = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (count < 0) throw new EOFException("Conexão interrompida durante o arquivo.");
                file.write(buffer, 0, count);
                remaining -= count;
                listener.onProgress("Recebendo " + destination.getName(), size == 0 ? 100 : (int)((size-remaining)*100/size));
            }
        }
        listener.onProgress("", 100);
        listener.onFileReceived(destination);
    }

    public void sendText(String mensagem) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) {
                    listener.onError("Nenhum dispositivo conectado.");
                    return;
                }
                try {
                    out.writeUTF("TEXT");
                    out.writeUTF(mensagem);
                    out.flush();
                } catch (IOException e) {
                    listener.onError("Falha ao enviar mensagem: " + e.getMessage());
                }
            }
        });
    }

    public void sendClipboard(String clipboardText) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) {
                    listener.onError("Nenhum dispositivo conectado.");
                    return;
                }
                try {
                    out.writeUTF("CLIPBOARD");
                    out.writeUTF(clipboardText);
                    out.flush();
                } catch (IOException e) {
                    listener.onError("Falha ao enviar área de transferência: " + e.getMessage());
                }
            }
        });
    }

    public void sendCommand(String comando, String parametro) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) {
                    listener.onError("Nenhum dispositivo conectado.");
                    return;
                }
                try {
                    out.writeUTF("CMD");
                    out.writeUTF(comando);
                    out.writeUTF(parametro != null ? parametro : "");
                    out.flush();
                } catch (IOException e) {
                    listener.onError("Falha ao enviar comando: " + e.getMessage());
                }
            }
        });
    }

    public void sendFile(File file) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) { listener.onError("Nenhum dispositivo conectado."); return; }
                long size = file.length(), sent = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                    out.writeUTF("FILE"); out.writeUTF(file.getName()); out.writeLong(size);
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        out.write(buffer, 0, count); sent += count;
                        listener.onProgress("Enviando " + file.getName(), size == 0 ? 100 : (int)(sent*100/size));
                    }
                    out.flush(); listener.onProgress("", 100); listener.onFileSent(file);
                } catch (IOException e) { listener.onError("Falha ao enviar arquivo: " + e.getMessage()); }
            }
        });
    }

    public void sendFiles(List<File> files) {
        for (File f : files) {
            if (f != null && f.exists() && f.isFile()) {
                sendFile(f);
            }
        }
    }

    public void sendDirData(String requestedPath) {
        sendExecutor.submit(() -> {
            synchronized (outLock) {
                if (out == null) return;
                try {
                    File dir = (requestedPath == null || requestedPath.isEmpty() || "ROOT".equalsIgnoreCase(requestedPath))
                            ? null : new File(requestedPath);

                    List<DirItem> items = new ArrayList<>();
                    String currentPath;

                    if (dir == null || !dir.exists() || !dir.isDirectory()) {
                        currentPath = "ROOT";
                        String home = System.getProperty("user.home");
                        addShortcutIfValid(items, "Área de Trabalho", new File(home, "Desktop"));
                        addShortcutIfValid(items, "Downloads", new File(home, "Downloads"));
                        addShortcutIfValid(items, "Documentos", new File(home, "Documents"));
                        addShortcutIfValid(items, "Imagens", new File(home, "Pictures"));
                        addShortcutIfValid(items, "Vídeos", new File(home, "Videos"));

                        File[] roots = File.listRoots();
                        if (roots != null) {
                            for (File r : roots) {
                                items.add(new DirItem(true, "Disco (" + r.getAbsolutePath() + ")", r.getAbsolutePath(), 0));
                            }
                        }
                    } else {
                        currentPath = dir.getAbsolutePath();
                        File[] list = dir.listFiles();
                        if (list != null) {
                            Arrays.sort(list, (a, b) -> {
                                if (a.isDirectory() && !b.isDirectory()) return -1;
                                if (!a.isDirectory() && b.isDirectory()) return 1;
                                return a.getName().compareToIgnoreCase(b.getName());
                            });

                            for (File f : list) {
                                try {
                                    if (!f.isHidden()) {
                                        items.add(new DirItem(f.isDirectory(), f.getName(), f.getAbsolutePath(), f.length()));
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }

                    out.writeUTF("DIR_DATA");
                    out.writeUTF(currentPath);
                    out.writeInt(items.size());
                    for (DirItem item : items) {
                        out.writeBoolean(item.isDir);
                        out.writeUTF(item.name);
                        out.writeUTF(item.fullPath);
                        out.writeLong(item.size);
                    }
                    out.flush();
                } catch (IOException e) {
                    listener.onError("Erro ao listar diretório: " + e.getMessage());
                }
            }
        });
    }

    private void addShortcutIfValid(List<DirItem> list, String label, File folder) {
        if (folder.exists() && folder.isDirectory()) {
            list.add(new DirItem(true, label + " (" + folder.getAbsolutePath() + ")", folder.getAbsolutePath(), 0));
        }
    }

    public void stop() {
        rodando = false;
        AudioPlaybackManager.stop();
        fecharSocketCliente();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        if (discoveryServer != null) {
            discoveryServer.stop();
        }
        sendExecutor.shutdownNow();
    }

    private void fecharSocketCliente() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {}
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
