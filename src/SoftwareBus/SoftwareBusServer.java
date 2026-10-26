package SoftwareBus;

public class SoftwareBusServer {

    public static SoftwareBusServer start(int listenPort, String routerHost, int routerPort) {
        return new SoftwareBusServer();   // TODO: open server socket, start router link
    }

    public static SoftwareBusServer start(int listenPort) {
        return start(listenPort, null, 0);   // no router
    }

    //TODO: replace this with actual implementation, this is only here so that it will routerlink will compile
    String name() {
        return "Bus";
    }

    void publishFromRouter(Message m) {
        // TODO: deliver m to local subscribers
    }
}
