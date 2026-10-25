package VirtualRouter;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable{

    private final Socket socket;
    private final RoutingHandler router;
    private PrintWriter out;

    public ClientHandler(Socket socket, RoutingHandler router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        try (Socket clientSocket = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(
                             socket.getInputStream(),
                             StandardCharsets.UTF_8
                     )
             );
             PrintWriter writer = new PrintWriter(
                     new OutputStreamWriter(
                             socket.getOutputStream(),
                             StandardCharsets.UTF_8
                     ),
                     true
             )) {
            this.out = writer;

            // Register client on router
            router.addClient(this);
            String clientName = socket.getRemoteSocketAddress().toString(); // May want to use a different id system

            // Start listening for messages
            String message;
            while ((message = in.readLine()) != null) { // Readline currently assumes a whole line is sent. May want to make this read a specific number of bytes instead.
                System.out.println(clientName + ": " + message);
                // Forward
                router.broadcast(message, this);
            }
        } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            // Remove client upon disconnect
            router.removeClient(this);
        }
    }

    public synchronized void send(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
