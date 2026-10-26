package SoftwareBus;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * The bus's TCP connection to the Virtual Router, on its own thread.
 *
 * Connects (and reconnects every few seconds if the router is down or drops),
 * so buses and the router can be started in any order. Incoming messages are
 * delivered to local subscribers; The link forwards local publications.
 * If the router is unreachable, local delivery keeps working and outgoing
 * messages are dropped with a terminal printout.
 */

public class RouterLink {

    private static final int RETRY_MS = 2000;
    private static final int CONNECT_TIMEOUT_MS = 3000;

    private final SoftwareBusServer bus;
    private final String host;
    private final int port;
    private final String who;
    private final Object writeLock = new Object();
    private volatile Socket socket;
    private volatile DataOutputStream out;
    private volatile boolean running = true;

    RouterLink(SoftwareBusServer bus, String host, int port) {
        this.bus = bus;
        this.host = host;
        this.port = port;
        this.who = bus.name() + "/router-link";
    }

    void start() {
        new Thread(this::run, who).start();
    }

    private void run() {
        boolean warned = false;
        while (running) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                synchronized (writeLock) {
                    socket = s;
                    out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                }
                System.out.println(who + ": connected to router at " + host + ":" + port);
                warned = false;
                while (running) {
                    bus.publishFromRouter(Protocol.readMessage(in));
                }
            } catch (IOException e) {
                if (!running) break;
                if (out != null) {
                    System.err.println(who + ": lost router connection: "
                            + (e instanceof EOFException ? "router closed the connection" : e.getMessage()));
                } else if (!warned) {
                    System.out.println(who + ": router " + host + ":" + port
                            + " not reachable yet; retrying every " + RETRY_MS / 1000 + "s");
                    warned = true;
                }
            } finally {
                synchronized (writeLock) {
                    out = null;
                    socket = null;
                }
            }
            try {
                Thread.sleep(RETRY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // Sends a locally published message to the router (dropped if not connected).
    void send(Message m) {
        synchronized (writeLock) {
            if (out == null) {
                System.out.println(who + ": router not connected; " + m + " delivered locally only");
                return;
            }
            try {
                Protocol.writeMessage(out, m);
                out.flush();
            } catch (IOException e) {
                System.err.println(who + ": send to router failed: " + e.getMessage());
                try { socket.close(); } catch (IOException ignored) {}   // reader thread will reconnect
            }
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
