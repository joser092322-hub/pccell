package com.example.pccell;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class DiscoveryClient {

    public interface DiscoveryListener {
        void onServerFound(String serverName, String host, int port);
        void onDiscoveryFinished();
    }

    public static final int DISCOVERY_PORT = 5055;

    public static void discover(Context context, DiscoveryListener listener) {
        new Thread(() -> {
            WifiManager.MulticastLock multicastLock = null;
            DatagramSocket socket = null;
            try {
                WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    multicastLock = wifi.createMulticastLock("pccell_multicast_lock");
                    multicastLock.setReferenceCounted(true);
                    multicastLock.acquire();
                }

                socket = new DatagramSocket();
                socket.setBroadcast(true);
                socket.setSoTimeout(3500);

                // Envia pacote de broadcast para descobrir o PC
                byte[] requestData = "PCCELL_DISCOVER".getBytes(StandardCharsets.UTF_8);

                // Tenta broadcast global e broadcast da subnet local
                InetAddress broadcastAddr = getBroadcastAddress(context);
                DatagramPacket packet = new DatagramPacket(requestData, requestData.length, broadcastAddr, DISCOVERY_PORT);
                socket.send(packet);

                DatagramPacket globalPacket = new DatagramPacket(
                        requestData, requestData.length,
                        InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
                );
                socket.send(globalPacket);

                byte[] buffer = new byte[1024];
                long endTime = System.currentTimeMillis() + 3500;

                while (System.currentTimeMillis() < endTime) {
                    try {
                        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                        socket.receive(responsePacket);

                        String msg = new String(responsePacket.getData(), 0, responsePacket.getLength(), StandardCharsets.UTF_8).trim();
                        if (msg.startsWith("PCCELL_SERVER:")) {
                            String[] parts = msg.split(":");
                            String serverName = parts.length > 1 ? parts[1] : "PC";
                            int port = 5050;
                            if (parts.length > 2) {
                                try { port = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
                            }
                            String ip = responsePacket.getAddress().getHostAddress();
                            if (listener != null) {
                                listener.onServerFound(serverName, ip, port);
                            }
                            break;
                        }
                    } catch (Exception timeoutOrReceive) {
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                if (multicastLock != null && multicastLock.isHeld()) {
                    multicastLock.release();
                }
                if (listener != null) {
                    listener.onDiscoveryFinished();
                }
            }
        }, "UDP-Discovery-Client").start();
    }

    private static InetAddress getBroadcastAddress(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return InetAddress.getByName("255.255.255.255");
            DhcpInfo dhcp = wifi.getDhcpInfo();
            if (dhcp == null) return InetAddress.getByName("255.255.255.255");

            int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) {
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            }
            return InetAddress.getByAddress(quads);
        } catch (Exception e) {
            try { return InetAddress.getByName("255.255.255.255"); } catch (Exception ignored) { return null; }
        }
    }
}
