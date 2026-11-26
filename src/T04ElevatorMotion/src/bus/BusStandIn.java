package T04ElevatorMotion.src.bus;

import java.util.*;
import Utilities.Message;
import bus.BusMessageDispatcher;

/**
 * In-memory stand-in for the software bus. Safe to use from several threads.
 * One lock guards every connection so a publish from one elevator's thread can never
 * corrupt a queue that another elevator's thread is reading.
 */
public class BusStandIn {

    private final List<Connection> connections = new ArrayList<>();
    private static final int MAX_QUEUE_SIZE = 1024;

    //One lock for the whole bus. Never one lock per connection: publish touches two connections
    //at once, and two locks taken in opposite orders by two threads would deadlock.
    private final Object lock = new Object();

    /**
     * Creates and returns a connection to the selected bus implementation.
     *
     * @return the connected message dispatcher
     */
    public BusMessageDispatcher connect() {
        synchronized (lock) {
            Connection connection = new Connection();
            connections.add(connection);
            return connection;
        }
    }

    //Helper Methods

    /**
     * @param topic topic number
     * @param subtopic subtopic number
     * @return the resulting integer value
     */
    private static int key(int topic, int subtopic) {
        return topic * 256 + subtopic;
    }


    /**
     * Represents one client connection to the in-memory bus stand-in.
     */
    private class Connection implements BusMessageDispatcher {

        private final Map<Integer, Deque<Message>> queues = new HashMap<>();

        /**
         * Registers the device or connection with the bus.
         */
        @Override
        public void register() {
            //The real bus will print a line
        }

        /**
         * Subscribes to messages matching the supplied topic and subtopic.
         *
         * @param topic topic number
         * @param subtopic subtopic number
         * @throws IllegalArgumentException if an argument or state violates the method’s requirements
         */
        @Override
        public void subscribe(int topic, int subtopic) {
            if (topic < 1 || topic > BusCodes.MAX_TOPIC || subtopic < 0 || subtopic > 255) {
                throw new IllegalArgumentException("Invalid topic or subtopic");
            }
            //Subscribing twice must not wipe the queue, computeIfAbsent handles this
            synchronized (lock) {
                //Subscribing twice must not wipe the queue, computeIfAbsent handles this
                queues.computeIfAbsent(key(topic, subtopic), k -> new ArrayDeque<>());
            }
        }

        /**
         * Removes the subscription matching the supplied topic and subtopic.
         *
         * @param topic topic number
         * @param subtopic subtopic number
         * @throws IllegalArgumentException if an argument or state violates the method’s requirements
         */
        @Override
        public void unsubscribe(int topic, int subtopic) {
            if (topic < 1 || topic > BusCodes.MAX_TOPIC || subtopic < 0 || subtopic > 255) {
                throw new IllegalArgumentException("Invalid topic or subtopic");
            }
            synchronized (lock) {
                queues.remove(key(topic, subtopic));
            }
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
            synchronized (lock) {
                for (Connection other : connections) {
                    if (other != this) {
                        other.receive(message);
                    }
                }
            }
        }

        /**
         * Same rule as Team03's bus (Message.matches):
         * Topics must match exactly
         * Subtopic 0 is a wildcard on either side.
         * A subscription on subtopic 0 gets every elevator
         * A message published with subtopic 0 reaches every subscription of that topic.
         */
        private void receive(Message message) {
            int topic = message.topic();
            int subtopic = message.subtopic();

            for (Map.Entry<Integer, Deque<Message>> entry : queues.entrySet()) {
                int queueTopic = entry.getKey() / 256;
                int queueSubtopic = entry.getKey() % 256;
                if (queueTopic == topic
                        && (queueSubtopic == BusCodes.ALL || subtopic == BusCodes.ALL || queueSubtopic == subtopic)) {
                    addDroppingOldest(entry.getValue(), message);
                }
            }
        }

        /**
         * Same as Team03's bu; when a queue is full the oldest message is thrown away to make room for the new one
         *
         * @param queue value for queue
         * @param message description printed when the check fails
         */
        private void addDroppingOldest(Deque<Message> queue, Message message) {
            while (queue.size() >= MAX_QUEUE_SIZE) {
                queue.pollFirst();
            }
            queue.addLast(message);
        }

        /**
         * Retrieves the next queued message for the supplied topic and subtopic, if one is available.
         *
         * @param topic topic number
         * @param subtopic subtopic number
         * @return the resulting Optional value
         * @throws IllegalArgumentException if an argument or state violates the method’s requirements
         */
        @Override
        public Optional<Message> get(int topic, int subtopic) {
            if (topic < 1 || topic > BusCodes.MAX_TOPIC || subtopic < 0 || subtopic > 255) {
                throw new IllegalArgumentException("topic must be 1 to " + BusCodes.MAX_TOPIC + " and subtopic 0 to 255");
            }
            synchronized (lock) {
                Deque<Message> queue = queues.get(key(topic, subtopic));
                if (queue == null || queue.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(queue.pollFirst());   //takes the oldest and removes it
            }
        }


    }
}
