package T04ElevatorMotion.src.bus;

import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import Utilities.Message;
import T04ElevatorMotion.src.bus.BusMessageDispatcher;

/**
 * Talks to Team03's real Software Bus over a TCP socket, using the same BusMessageDispatcher methods as BusStandIn.
 *
 * The real bus never pushes messages, we have to ask for the messages with GET. So a small background thread asks for
 * every subscription over and over and puts what it finds in a local inbox. get() only reads that inbox and
 * never waits on the network, so the cabin's thread is not slowed down by the connection.
 *
 * Everything written to the socket goes through one lock, so a GET and its reply are never split by another request.
 * Use one SocketBusDispatcher per car; the bus does not give a connection its own messages back.
 *
 * If the connection breaks, the dispatcher stops (get returns nothing, publish does nothing) and getFailure()
 * says why. It never throws for network trouble, because the car's thread must keep simulating.
 */
public class SocketBusDispatcher implements BusMessageDispatcher, AutoCloseable {

    private static final int MAX_QUEUE_SIZE = 1024;
    private static final int READ_TIMEOUT_MS = 5000;     //Team03's README: never let a GET hang forever
    private static final long IDLE_PAUSE_MS = 2;         //pause when a pass over all subscriptions found nothing
    private static final long RETRY_PAUSE_MS = 100;

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final Object wire = new Object();     //one request on the socket at a time
    private final ConcurrentHashMap<Integer, ConcurrentLinkedDeque<Message>> inboxes = new ConcurrentHashMap<>();
    private final Thread poller;

    private volatile boolean closed = false;
    private volatile IOException failure = null;
    private volatile int id = 0;

    /**
     * Connects to the bus. The bus may still be starting, so a refused connection is retried every 0.1 s,
     * up to attempts times (Team03's README suggests about 50).
     *
     * @param host software-bus host name or IP address
     * @param port software-bus TCP port number
     * @param attempts maximum number of connection attempts
     * @return the connected socket dispatcher
     * @throws IOException if the operation cannot be completed
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    public static SocketBusDispatcher connect(String host, int port, int attempts) throws IOException {
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be at least 1");
        }
        ConnectException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return new SocketBusDispatcher(new Socket(host, port));
            } catch (ConnectException e) {
                last = e;
                try {
                    Thread.sleep(RETRY_PAUSE_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }

    /**
     * Creates and initializes the SocketBusDispatcher instance.
     *
     * @param socket connected client socket
     * @throws IOException if the operation cannot be completed
     */
    private SocketBusDispatcher(Socket socket) throws IOException {
        this.socket = socket;
        socket.setSoTimeout(READ_TIMEOUT_MS);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = socket.getOutputStream();
        this.poller = new Thread(this::pollLoop, "bus-poller");
        poller.setDaemon(true);
        poller.start();
    }

    /** The ID the bus gave us, or 0 before register() has finished. */
    public int getId() {
        return id;
    }

    /**
     * Reports whether the socket connection is currently active.
     *
     * @return whether the requested condition is true
     */
    public boolean isConnected() {
        return failure == null && !closed;
    }

    /** Why the connection stopped, or null if it has not. */
    public IOException getFailure() {
        return failure;
    }

    /**
     * Registers the device or connection with the bus.
     *
     * @throws IOException if the operation cannot be completed
     * @throws IllegalStateException if an argument or state violates the method’s requirements
     */
    @Override
    public void register() {
        synchronized (wire) {
            if (id != 0 || !isConnected()) {
                return;
            }
            try {
                writeFrame(Message.of(BusCodes.REGISTER, 0, 0));
                Message reply = readFrame();     //REGISTER always gets exactly one reply, ID in the subtopic byte
                if (reply.topic() != BusCodes.REGISTER) {
                    throw new IOException("expected a REGISTER reply, got " + reply);
                }
                id = reply.subtopic();
            } catch (IOException e) {
                fail(e);
                return;
            }
        }
        if (id == 0) {
            throw new IllegalStateException("the bus refused REGISTER (no IDs left)");
        }
    }

    /**
     * Subscribes to messages matching the supplied topic and subtopic.
     *
     * @param topic topic number
     * @param subtopic subtopic number
     */
    @Override
    public void subscribe(int topic, int subtopic) {
        checkTopicAndSubtopic(topic, subtopic);
        //Create the inbox first, so nothing the bus sends after SUBSCRIBE can be lost
        if (inboxes.putIfAbsent(key(topic, subtopic), new ConcurrentLinkedDeque<>()) == null) {
            send(Message.of(BusCodes.SUBSCRIBE, topic, subtopic));
            //The bus does not reply to SUBSCRIBE and handles each connection on its own thread, so a message
            //published by someone else right after this call could be missed. One GET on this same connection
            //gets answered only after the SUBSCRIBE was processed, so when it returns the subscription is active.
            drain(key(topic, subtopic));
        }
    }

    /**
     * Removes the subscription matching the supplied topic and subtopic.
     *
     * @param topic topic number
     * @param subtopic subtopic number
     */
    @Override
    public void unsubscribe(int topic, int subtopic) {
        checkTopicAndSubtopic(topic, subtopic);
        inboxes.remove(key(topic, subtopic));
        send(Message.of(BusCodes.UNSUBSCRIBE, topic, subtopic));
    }

    /**
     * Publishes a message to the connected bus.
     *
     * @param message description printed when the check fails
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    @Override
    public void publish(Message message) {
        if (message == null || message.topic() < 1 || message.topic() > BusCodes.MAX_TOPIC) {
            throw new IllegalArgumentException("message must not be null and its topic must be 1 to " + BusCodes.MAX_TOPIC);
        }
        send(message);
    }

    /**
     * Retrieves the next queued message for the supplied topic and subtopic, if one is available.
     *
     * @param topic topic number
     * @param subtopic subtopic number
     * @return the resulting Optional value
     */
    @Override
    public Optional<Message> get(int topic, int subtopic) {
        checkTopicAndSubtopic(topic, subtopic);
        ConcurrentLinkedDeque<Message> inbox = inboxes.get(key(topic, subtopic));
        if (inbox == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(inbox.pollFirst());     //oldest first
    }

    /**
     * Closes the component and releases its associated resources.
     */
    @Override
    public void close() {
        closed = true;
        poller.interrupt();
        try {
            socket.close();
        } catch (IOException ignored) {
            //nothing useful to do
        }
    }

    /**
     * Background thread: ask the bus for the queued messages of every subscription, again and again
     */
    private void pollLoop() {
        while (isConnected()) {
            boolean foundAny = false;
            for (Integer key : inboxes.keySet()) {
                if (drain(key)) {
                    foundAny = true;
                }
            }
            if (!foundAny) {
                try {
                    Thread.sleep(IDLE_PAUSE_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * GET until NO_MESSAGE for one subscription. Returns true if at least one message came.
     *
     * @param key value for key
     * @return whether the requested condition is true
     */
    private boolean drain(int key) {
        boolean any = false;
        while (isConnected()) {
            Message reply;
            synchronized (wire) {
                if (!inboxes.containsKey(key)) {
                    return any;       //unsubscribed in the meantime
                }
                try {
                    writeFrame(Message.of(BusCodes.GET, key / 256, key % 256));
                    reply = readFrame();
                } catch (IOException e) {
                    fail(e);
                    return any;
                }
            }
            if (reply.topic() == BusCodes.NO_MESSAGE) {
                return any;
            }
            ConcurrentLinkedDeque<Message> inbox = inboxes.get(key);
            if (inbox != null) {
                //Same as the real bus: when the queue is full the oldest message is dropped
                while (inbox.size() >= MAX_QUEUE_SIZE) {
                    inbox.pollFirst();
                }
                inbox.addLast(reply);
            }
            any = true;
        }
        return any;
    }

    /**
     * Queues or sends a message to the connected router or bus endpoint.
     *
     * @param message description printed when the check fails
     */
    private void send(Message message) {
        synchronized (wire) {
            if (!isConnected()) {
                return;
            }
            try {
                writeFrame(message);
            } catch (IOException e) {
                fail(e);
            }
        }
    }

    /**
     * Writes one message frame to the socket output stream.
     *
     * @param message description printed when the check fails
     * @throws IOException if the operation cannot be completed
     */
    private void writeFrame(Message message) throws IOException {
        out.write(message.toBytes());
        out.flush();
    }

    /**
     * Reads and decodes one message frame from the socket input stream.
     *
     * @return the created or decoded message
     * @throws IOException if the operation cannot be completed
     */
    private Message readFrame() throws IOException {
        byte[] word = new byte[Message.SIZE];
        in.readFully(word);        //throws EOFException if the bus closed the connection
        return Message.fromBytes(word);
    }

    /**
     * Records an I/O failure and updates the dispatcher connection state.
     *
     * @param e value for e
     */
    private void fail(IOException e) {
        if (failure == null && !closed) {
            failure = e;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            //nothing useful to do
        }
    }

    /**
     * Builds the integer key used to index a topic/subtopic pair.
     *
     * @param topic topic number
     * @param subtopic subtopic number
     * @return the resulting integer value
     */
    private static int key(int topic, int subtopic) {
        return topic * 256 + subtopic;
    }

    /**
     * Validates the supplied topic and subtopic values.
     *
     * @param topic topic number
     * @param subtopic subtopic number
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    private static void checkTopicAndSubtopic(int topic, int subtopic) {
        if (topic < 1 || topic > BusCodes.MAX_TOPIC || subtopic < 0 || subtopic > 255) {
            throw new IllegalArgumentException("topic must be 1 to " + BusCodes.MAX_TOPIC + " and subtopic 0 to 255");
        }
    }
}