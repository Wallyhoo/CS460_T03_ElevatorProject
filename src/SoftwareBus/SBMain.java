package SoftwareBus;

/**
 * Runs a Software Bus as a stand-alone
 *
 * Usage:
 *   java SoftwareBus.SBMain &lt;listenPort&gt; [routerHost routerPort]
 *
 * Example:
 *   java SoftwareBus.SBMain 6000                     stand-alone bus, no router
 *   java SoftwareBus.SBMain 6000 localhost 5000      bus + router on this machine
 *   java SoftwareBus.SBMain 6000 192.168.8.10 5000   router on another machine
 */

public class SBMain {

    public static void main(String[] args) throws Exception {
        if (args.length != 1 && args.length != 3) {
            System.err.println("Usage: java SoftwareBus.SBMain <listenPort> [routerHost routerPort]");
            System.exit(1);
        }
        int listenPort = Integer.parseInt(args[0]);
        if (args.length == 3) {
            SoftwareBusServer.start(listenPort, args[1], Integer.parseInt(args[2]));
        } else {
            SoftwareBusServer.start(listenPort);
        }
        // The bus runs on its own thread
    }
}
