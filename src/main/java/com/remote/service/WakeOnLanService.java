package com.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Service
public class WakeOnLanService {

    @Value("${power.wol.broadcast-address:255.255.255.255}")
    private String broadcastAddress;

    @Value("${power.wol.port:9}")
    private int wolPort;

    public void wake(String macAddress) {
        try {
            byte[] macBytes = parseMacAddress(macAddress);
            byte[] magicPacket = new byte[6 + 16 * macBytes.length];

            for (int i = 0; i < 6; i++) {
                magicPacket[i] = (byte) 0xFF;
            }

            for (int i = 6; i < magicPacket.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, magicPacket, i, macBytes.length);
            }

            InetAddress address = InetAddress.getByName(broadcastAddress);

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                socket.send(new DatagramPacket(magicPacket, magicPacket.length, address, wolPort));
            }

            System.out.println("Wake-on-LAN пакет отправлен на MAC: " + macAddress);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка отправки Wake-on-LAN пакета", e);
        }
    }

    private byte[] parseMacAddress(String macAddress) {
        String cleanMac = macAddress.replace(":", "").replace("-", "");

        if (cleanMac.length() != 12) {
            throw new IllegalArgumentException("Некорректный MAC-адрес: " + macAddress);
        }

        byte[] result = new byte[6];

        for (int i = 0; i < 6; i++) {
            result[i] = (byte) Integer.parseInt(cleanMac.substring(i * 2, i * 2 + 2), 16);
        }

        return result;
    }
}