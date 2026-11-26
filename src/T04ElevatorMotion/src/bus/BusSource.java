package T04ElevatorMotion.src.bus;

import bus.BusMessageDispatcher;
import bus.BusStandIn;
import bus.SocketBusDispatcher;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decides where every connection to the bus comes from, so Main does not have to know.
 * With no option it is our in-process BusStandIn. With --bus=host:port (for example --bus=localhost:6100), or
 * two plain arguments "host port" like Team03's README, every connection is a SocketBusDispatcher to Team03's
 * real Software Bus. Each car and each harness calls open() once,
 * because the real bus does not give a connection its own messages back.
 */
public class BusSource implements AutoCloseable {

    private static final String OPTION = "--bus=";

    private final String host;                  //null means the stand-in
    private final int port;
    private final BusStandIn standIn;           //null means the real bus
    private final List<SocketBusDispatcher> opened = new ArrayList<>();
    private int connectAttempts = 50;           //Team03's README suggests about 50 (5 seconds)

    /** The in-process stand-in bus. */
    public BusSource() {
        this.host = null;
        this.port = 0;
        this.standIn = new BusStandIn();
    }

    /** Team03's real bus at host:port.
     *
     * @param host software-bus host name or IP address
     * @param port software-bus TCP port number
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    public BusSource(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("bus host must not be empty");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("bus port must be 1 to 65535: " + port);
        }
        this.host = host;
        this.port = port;
        this.standIn = null;
    }

    /**
     * Reads the command-line arguments. Two forms connect to the real bus: --bus=host:port, or Team03's own
     * convention of two plain arguments, host then port (192.168.8.10 6000). No bus arguments means the stand-in.
     *
     * @param arguments command-line arguments
     * @return the configured bus source
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    public static BusSource fromArguments(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.startsWith(OPTION)) {
                String value = argument.substring(OPTION.length());
                int colon = value.lastIndexOf(':');
                if (colon < 1 || colon == value.length() - 1) {
                    throw new IllegalArgumentException("expected --bus=host:port, got " + argument);
                }
                return realBus(value.substring(0, colon), value.substring(colon + 1), argument);
            }
        }
        if (arguments.size() == 2 && !arguments.get(0).startsWith("--") && !arguments.get(1).startsWith("--")) {
            return realBus(arguments.get(0), arguments.get(1), arguments.get(0) + " " + arguments.get(1));
        }
        return new BusSource();
    }

    /**
     * Builds a source that connects to the real software bus.
     *
     * @param host software-bus host name or IP address
     * @param port software-bus TCP port number
     * @param whole original argument text used in an error message
     * @return the configured bus source
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    private static BusSource realBus(String host, String port, String whole) {
        try {
            return new BusSource(host, Integer.parseInt(port));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("the bus port must be a number: " + whole);
        }
    }

    /** Fewer retries make a missing bus fail faster (the checks use this).
     *
     * @param attempts maximum number of connection attempts
     * @return the configured bus source
     */
    public BusSource retries(int attempts) {
        this.connectAttempts = attempts;
        return this;
    }

    /**
     * Reports whether this source uses the real socket-based software bus.
     *
     * @return whether the requested condition is true
     */
    public boolean isRealBus() {
        return standIn == null;
    }

    /**
     * Returns a readable description of the supplied value.
     *
     * @return the resulting text
     */
    public String describe() {
        return isRealBus() ? "Team03's Software Bus at " + host + ":" + port : "in-process stand-in";
    }

    /** A new connection. For the real bus this connects and registers, and throws if the bus cannot be reached.
     *
     * @return the connected message dispatcher
     * @throws IOException if the operation cannot be completed
     */
    public synchronized BusMessageDispatcher open() throws IOException {
        if (standIn != null) {
            return standIn.connect();
        }
        try {
            SocketBusDispatcher connection = SocketBusDispatcher.connect(host, port, connectAttempts);
            connection.register();
            opened.add(connection);
            return connection;
        } catch (ConnectException e) {
            throw new IOException("Could not connect to the Software Bus at " + host + ":" + port
                    + " (is it running?)", e);
        }
    }

    /** Closes every real-bus connection that was opened. The stand-in has nothing to close. */
    @Override
    public synchronized void close() {
        for (SocketBusDispatcher connection : opened) {
            connection.close();
        }
        opened.clear();
    }
}