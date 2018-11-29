package com.smddzcy.eventmanager;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class SocketUtils {
    public static final String HOST_IP = "ec2-35-180-63-125.eu-west-3.compute.amazonaws.com";
    public static final int HOST_PORT = 63012;

    public static Socket getSocket() {
        Socket socket = null;
        while (socket == null) {
            try {
                socket = new Socket(HOST_IP, HOST_PORT);
            } catch (Exception e) {
                Log.e("SendMessage", "Error while connecting to server: " + e.getMessage());
                Log.d("SendMessage", "Retrying connection in 500ms.");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return socket;
    }

    public static String sendMessage(String msg) {
        Socket socket = getSocket();
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String result = sendMessage(msg, socket, in, out);

            socket.close();
            in.close();
            out.close();

            return result;
        } catch (IOException ignored) {
        }
        return "{\"type\": \"fail\", \"payload\": \"Couldn't send the message to server\"}";
    }

    public static String sendMessage(String msg, Socket socket, BufferedReader in, BufferedWriter out) {
        String result = null;
        while (result == null) {
            try {
                out.write(msg);
                out.newLine();
                out.flush();
                result = in.readLine();
            } catch (Exception e) {
                Log.e("SendMessage", "Error while sending message to server: " + e.getMessage());
                Log.d("SendMessage", "Retrying connection in 500ms.");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return result;
    }
}
