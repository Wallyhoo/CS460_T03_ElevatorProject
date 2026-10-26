package SoftwareBus;


import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SoftwareBusServer {

    private ServerSocket serverSocket;
    private RouterLink routerLink;

    // open server socket, start router link
    public static SoftwareBusServer start(int listenPort, String routerHost, int routerPort) throws IOException {
        SoftwareBusServer bus = new SoftwareBusServer();
        bus.serverSocket = new ServerSocket(listenPort);

        // start router link
        try {
            bus.routerLink = new RouterLink(bus, routerHost, routerPort);
            bus.routerLink.start();
        } catch (RuntimeException e) {
            throw new IOException(e);
        }

        return bus;
        //return new SoftwareBusServer();
    }

    public static SoftwareBusServer start(int listenPort) throws IOException {
        return start(listenPort, null, 0);   // no router
    }

    //TODO: replace this with actual implementation, this is only here so that it will routerlink will compile
    String name() {
        return "Bus";
    }

    // deliver m to local subscribers
    void publishFromRouter(Message m) {
        routerLink.send(m);
    }
}
