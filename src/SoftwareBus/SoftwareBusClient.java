package SoftwareBus;

import Utilities.Message;
import Utilities.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class SoftwareBusClient implements Runnable{

    private final Socket socket;
    private final MessageBus bus;

    private final InputStream in;
    private final OutputStream out;

    private int id = 0;

    private record TopicKey(int topic, int subtopic) {}

    private static final int MAX_QUEUE_SIZE =1024;

    private final ConcurrentHashMap<TopicKey, BlockingQueue<Message>> topicQueues = new ConcurrentHashMap<>();

    private boolean running = true;

    public SoftwareBusClient(Socket socket, MessageBus bus) throws IOException {
        this.socket = socket;
        this.bus = bus;

        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    // Runs the listening loop for the connected client
    @Override
    public void run() {
        bus.addClient(this);

        try (socket) {
            while (running) {
                try {
                    Message message = Protocol.readMessage(in);
                    // null pointer error checking
                    if (message == null) {
                        throw new NullPointerException("ERROR! message is null! message must never be null!");
                    }
                    // Handle subscription methods here:
                    switch (message.topic()) {
                        case Protocol.REGISTER -> {
                            // Request a new id from the router.
                            this.id = register();

                            // Tell the client what its id is
                            Protocol.writeMessage(out, Message.of(Protocol.REGISTER, id, 0));
                            System.out.printf("Received REGISTER from client %d%n", id);
                        }
                        case Protocol.SUBSCRIBE -> {
                            // parse subtopic byte as topic and last byte of body as subtopic
                            int topic = message.subtopic();
                            int subtopic = message.body()[Message.BODY_SIZE - 1] & 0xFF;

                            System.out.printf("Received SUBSCRIBE from client %d: Topic %d, Subtopic %d%n", id, topic, subtopic);

                            subscribe(topic, subtopic);
                        }
                        case Protocol.UNSUBSCRIBE -> {
                            // Similar to above but used to remove topic
                            int topic = message.subtopic();
                            int subtopic = message.body()[Message.BODY_SIZE - 1] & 0xFF;

                            System.out.printf("Received UNSUBSCRIBE from client %d: Topic %d, Subtopic %d%n", id, topic, subtopic);

                            unsubscribe(topic, subtopic);
                        }
                        case Protocol.GET -> {
                            // Parse the message and call the getMessage function
                            int topic = message.subtopic();
                            int subtopic = message.body()[Message.BODY_SIZE - 1] & 0xFF;

                            System.out.printf("Received GET from client %d: Topic %d, Subtopic %d%n", id, topic, subtopic);

                            getMessage(topic, subtopic);
                        }
                        default ->
                            // If not a special message, broadcast to all other connections
                            publish(message);
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            System.out.println("Client connection error: " + e.getMessage());
        } finally {
            // Remove client upon disconnect
            bus.removeClient(this);
        }
    }

    private void subscribe(int topic, int subtopic) throws IOException {
        TopicKey key = new TopicKey(topic, subtopic);

        topicQueues.computeIfAbsent(
                key,
                k -> new ArrayBlockingQueue<>(MAX_QUEUE_SIZE)
        );
    }

    private void unsubscribe(int topic, int subtopic) {
        TopicKey key = new TopicKey(topic, subtopic);

        topicQueues.remove(key);
    }

    private int register() {
        return 0;
    }

    private void publish(Message m) {
        bus.broadcastFromClient(m, this);
    }

    // Takes a message and puts it in the correct queue
    public void pushMessage(Message m) {
        int topic = m.topic();
        int subtopic = m.subtopic();

        if (subtopic == 0) {
            // Broadcast to all subscriptions for this topic
            for (var entry : topicQueues.entrySet()) {
                if (entry.getKey().topic() == topic) {
                    entry.getValue().offer(m);
                }
            }
        } else {
            // Exact subscription
            BlockingQueue<Message> exact = topicQueues.get(new TopicKey(topic, subtopic));

            if (exact != null) {
                exact.offer(m);
            }
        }
    }

    // Sends a message from the corresponding topic queue to the client.
    private void getMessage(int topic, int subtopic) throws IOException {
        TopicKey key = new TopicKey(topic, subtopic);

        BlockingQueue<Message> queue = topicQueues.get(key);

        Message message = (queue == null) ? null : queue.poll();

        if (message != null) {
            Protocol.writeMessage(out, message);
        } else {
            // Send a response that no message is available
            Protocol.writeMessage(out, Message.of(Protocol.NO_MESSAGE, topic, subtopic));
        }
    }

    // closes i/o and socket
    public void close() throws IOException {
        bus.removeClient(this);
        running = false; // kill while loop
        // kill i/o streams
        out.flush();
        out.close();
        in.close();
        socket.close(); // kill socket
    }
}
