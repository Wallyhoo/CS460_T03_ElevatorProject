package T01DoorAssembly;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * DoorDash: delivers messages between the T03 Software Bus and the DoorManager.
 * This is the only file in the door code that uses a socket.
 *
 * It opens one socket to the bus and does four jobs:
 *   1. Subscribes to DOOR_COMMAND for the manager's car.
 *   2. Asks the bus for new messages every few milliseconds (the T03 bus never
 *      pushes; a program only gets a message when it sends GET).
 *   3. Hands each message it receives to DoorManager.onMessage().
 *   4. Sends the manager's status messages (DOOR_STATE, DOOR_OBSTRUCTION) to the bus.
 *
 * T03 bus requests are 8-byte messages whose first byte is the command:
 *   SUBSCRIBE [0x02][topic][0 0 0 0 0 subtopic]   no reply
 *   GET       [0x05][topic][0 0 0 0 0 subtopic]   reply: the oldest message, or [0x06]... if none
 * Any other message is a publish and is passed on to the other subscribers.
 * The bus never sends a program its own messages, and it keeps no history.
 */
public class DoorDash {

    private static final long POLL_MS = 20;
    private static final int TIMEOUT_MS = 3000; // connecting, and waiting for a GET reply

    private final DoorManager manager;
    private final String host;
    private final int port;
    private final Object writeLock = new Object();

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private Thread poller;
    private volatile boolean running = false;

    public DoorDash(DoorManager manager, String host, int port) {
        this.manager = manager;
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to the bus, subscribes, and starts delivering messages both ways.
     * Throws IOException if the bus cannot be reached.
     */
    public synchronized void start() throws IOException {
        if (running) return;
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            s.setSoTimeout(TIMEOUT_MS);
            s.setTcpNoDelay(true);
            socket = s;
            in = new DataInputStream(s.getInputStream());
            out = s.getOutputStream();
            write(DoorBus.encode(DoorBus.SUBSCRIBE, DoorBus.TOPIC_DOOR_COMMAND, manager.getCarId()));
        } catch (IOException e) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
            throw new IOException("Cannot reach the Software Bus at " + host + ":" + port + " (" + e.getMessage() + ")", e);
        }
        manager.attach(this::send);
        running = true;
        poller = new Thread(this::pollLoop, "DoorDash-Poll");
        poller.setDaemon(true);
        poller.start();
        System.out.println("[DoorDash] Connected to the Software Bus at " + host + ":" + port + ", car " + manager.getCarId());
    }

    public synchronized void stop() {
        boolean wasRunning = running;
        running = false;
        manager.detach();
        if (poller != null) {
            poller.interrupt();
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        if (wasRunning) {
            System.out.println("[DoorDash] Disconnected from the Software Bus");
        }
    }

    /** The manager's outgoing messages end up here. */
    private void send(byte[] message) {
        try {
            write(message);
        } catch (IOException e) {
            fail("could not send to the bus: " + e.getMessage());
        }
    }

    private void write(byte[] message) throws IOException {
        synchronized (writeLock) {
            out.write(message);
            out.flush();
        }
    }

    private void pollLoop() {
        try {
            while (running) {
                byte[] message;
                while (running && (message = get()) != null) {
                    manager.onMessage(message);
                }
                Thread.sleep(POLL_MS);
            }
        } catch (InterruptedException ignored) {
        } catch (IOException e) {
            if (running) fail("lost the bus connection: " + e.getMessage());
        }
    }

    /** Takes the oldest waiting message for this car, or null if there is none. */
    private byte[] get() throws IOException {
        write(DoorBus.encode(DoorBus.GET, DoorBus.TOPIC_DOOR_COMMAND, manager.getCarId()));
        byte[] reply = new byte[DoorBus.SIZE];
        in.readFully(reply);
        return (reply[0] & 0xFF) == DoorBus.NO_MESSAGE ? null : reply;
    }

    private void fail(String reason) {
        if (!running) return;
        System.out.println("[DoorDash] " + reason + " - stopping");
        stop();
    }
}
