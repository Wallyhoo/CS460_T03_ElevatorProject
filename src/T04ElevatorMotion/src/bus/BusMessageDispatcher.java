package T04ElevatorMotion.src.bus;


import java.util.Optional;
import Utilities.Message;

/**
 * Defines the elevator application’s interface for sending and receiving bus messages.
 */
public interface BusMessageDispatcher {

    /** Announces this connection to the bus. The real bus replies with the ID and SocketBusDispatcher reads it. */
    void register();

    /** Creates a queue for topic and subtopic on this connection. There is no callback, poll with get. */
    void subscribe(int topic, int subtopic);

    /** Removes the queue for topic and subtopic and any messages in it. */
    void unsubscribe(int topic, int subtopic);

    /** Sends a message to every other connection subscribed to its topic. The sender does not receive its own message. */
    void publish(Message message);

    /** Returns the oldest queued message for topic and subtopic, removes it, or empty if there is none. */
    Optional<Message> get(int topic, int subtopic);
}
