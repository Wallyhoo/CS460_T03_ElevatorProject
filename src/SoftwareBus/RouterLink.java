package SoftwareBus;

import Utilities.Message;
import Utilities.Protocol;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The bus's TCP connection to the Virtual Router, on its own thread.
 *
 * Connects (and reconnects every few seconds if the router is down or drops),
 * so buses and the router can be started in any order. Incoming messages are
 * delivered to local subscribers; The link forwards local publications.
 * If the router is unreachable, local delivery keeps working and outgoing
 * messages are dropped with a terminal printout.
 */

public class RouterLink implements Runnable {

    private static final int RETRY_MS = 2000;
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final MessageBus bus;
    private final String host;
    private final int port;
    private final BlockingQueue<Message> sendQueue = new LinkedBlockingQueue<>();
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean running = true;

    RouterLink(MessageBus bus, String host, int port) {
        this.bus = bus;
        this.host = host;
        this.port = port;
    }

    public void send(Message m) {
        if (m != null) {
            sendQueue.offer(m);
        }
    }

    @Override
    public void run() {
        bus.addLink(this);

        try {
            socket = new Socket(host, port);

            out = socket.getOutputStream();
            in = socket.getInputStream();

            while (running && !socket.isClosed()) {
                // Flush the message queue
                while (!sendQueue.isEmpty()) {
                    Message m = sendQueue.poll();
                    if (m != null) {
                        Protocol.writeMessage(out, m);
                    }
                }

                // Read and broadcast incoming messages
                Message m = Protocol.readMessage(in);
                bus.broadcastFromRouter(m, this);
            }
        } catch (IOException e) {
            System.out.println("Router connection error: " + e.getMessage());
        } finally {
            // Remove client upon disconnect
            bus.removeLink(this);
        }

    }

    void close() {
        running = false;
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }
}
