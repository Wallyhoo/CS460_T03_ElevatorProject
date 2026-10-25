package SoftwareBus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class SBMain { // Extends Thread

    private final static int portNumber = 49152; // ephemeral TCP port

    public static void TCPServer(String[] args) {
        System.out.println("Waiting for connection on TCP port " + portNumber);

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            Socket socket = serverSocket.accept();
            OutputStream output = socket.getOutputStream();
            // probably read in messages here
            do {
                InputStream input = socket.getInputStream();
            } while (true); // do we need an exit condition here?
            // serverSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
