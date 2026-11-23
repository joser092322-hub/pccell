import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Servidor UDP responsável por responder a requisições de descoberta
 * e emitir anúncios para que o celular encontre o PC automaticamente.
 */
public class DiscoveryServer {

    public static final int DISCOVERY_PORT = 5055;
    private final int tcpPort;
    private final String serverName;
    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread listenThread;
    private Thread broadcastThread;

    public DiscoveryServer(int tcpPort) {
        this.tcpPort = tcpPort;
        String user = System.getProperty("user.name", "PC");
        String host = "PC";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        this.serverName = host + " (" + user + ")";
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        listenThread = new Thread(this::listenForRequests, "UDP-Discovery-Listen");
        listenThread.setDaemon(true);
        listenThread.start();

        broadcastThread = new Thread(this::broadcastPeriodically, "UDP-Discovery-Broadcast");
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private void listenForRequests() {
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
            byte[] buffer = new byte[1024];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (message.startsWith("PCCELL_DISCOVER")) {
                    String response = "PCCELL_SERVER:" + serverName + ":" + tcpPort;
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket responsePacket = new DatagramPacket(
                            responseBytes, responseBytes.length,
                            packet.getAddress(), packet.getPort()
                    );
                    socket.send(responsePacket);
                }
            }
        } catch (SocketException e) {
            // Socket fechado normalmente no stop()
        } catch (Exception e) {
            if (running) {
                System.err.println("Erro no serviço de descoberta UDP: " + e.getMessage());
            }
        }
    }

    private void broadcastPeriodically() {
        while (running) {
            try {
                Thread.sleep(2500);
                if (!running || socket == null || socket.isClosed()) continue;

                String broadcastMsg = "PCCELL_SERVER:" + serverName + ":" + tcpPort;
                byte[] data = broadcastMsg.getBytes(StandardCharsets.UTF_8);

                DatagramPacket broadcastPacket = new DatagramPacket(
                        data, data.length,
                        InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                );
                socket.send(broadcastPacket);
            } catch (InterruptedException e) {
                break;
            } catch (Exception ignored) {
            }
        }
    }

    public String getServerName() {
        return serverName;
    }
}
