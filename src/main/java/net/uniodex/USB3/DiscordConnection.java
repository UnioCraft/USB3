package net.uniodex.USB3;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class DiscordConnection {

    public DiscordConnection(String address, int port, String text) {
        try {
            Socket socket = new Socket(address, port);
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);
            writer.println(text);
            output.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}