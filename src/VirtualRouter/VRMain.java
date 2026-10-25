package VirtualRouter;

import java.io.IOException;

public class VRMain {

    public static void main(String[] args) throws IOException {
        Server server = new Server(5555); //Using a temp port for now. Eventually parse port from args
        server.start();
    }
}
