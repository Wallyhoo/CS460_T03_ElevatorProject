package SoftwareBus;

public class SoftwareBusClient {
    public static SoftwareBusClient connect(String busHost, int busPort) {
        return new SoftwareBusClient();   // TODO: open socket to the bus
    }

    public int register() {
        return 0;   // TODO: send REGISTER, return id
    }

    public void subscribe(int topic, int subtopic) {
        // TODO: send SUBSCRIBE
    }

    public void unsubscribe(int topic, int subtopic) {
        // TODO: send UNSUBSCRIBE
    }

    public void publish(Message m) {
        // TODO: send PUBLISH
    }

    public Message getMessage(int topic, int subtopic) {
        return null;   // TODO: send GET; null if nothing waiting
    }

    //@Override
    public void close() {
        // TODO: close socket
    }
}
