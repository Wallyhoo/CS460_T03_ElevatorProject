package VirtualRouter;

import java.io.IOException;

public class VRMain {

    public static void main(String[] args) throws IOException {
        int port = 5555; // Default port

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Error: '" + args[0] + "' is not a valid port number.");
            }
        }

        Server server = new Server(port); //Using a temp port for now. Eventually parse port from args
        server.start();
    }
}
