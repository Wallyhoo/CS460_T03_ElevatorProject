package SoftwareBus;

import java.io.*;
import java.net.Socket;

public class SoftwareBusClient {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    private SoftwareBusClient(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public static SoftwareBusClient connect(String busHost, int busPort) throws IOException {
        try {
            return new SoftwareBusClient(new Socket(busHost, busPort));
        } catch (IOException e) {
            throw new IOException(e);
        }
        //return new SoftwareBusClient();   // TODO: open socket to the bus
    }

    public int register() {
//        try {
//            // need to register here
//        } catch (IOException e) {
//            throw new IOException(e);
//        }
        return 0;   // TODO: send REGISTER, return id
    }

    public void subscribe(int topic, int subtopic) {
        // TODO: send SUBSCRIBE
    }

    public void unsubscribe(int topic, int subtopic) {
        // TODO: send UNSUBSCRIBE
    }

    public void publish(Message m) throws IOException {
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
