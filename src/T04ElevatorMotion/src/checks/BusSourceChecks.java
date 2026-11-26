package T04ElevatorMotion.src.checks;

import SoftwareBus.MessageBus;
import SoftwareBus.SoftwareBusServer;
import Utilities.Message;
import bus.BusCodes;
import bus.BusMessageDispatcher;
import bus.BusSource;
import bus.SocketBusDispatcher;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Checks how the bus option is read and that both kinds of connection can talk. Uses port 6101 for Team03's bus. */
public class BusSourceChecks {

    private static final int PORT = 6101;

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) throws Exception {
        argumentChecks();
        standInChecks();
        realBusChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        System.exit(checksFailed > 0 ? 1 : 0);      //exit explicitly, the bus threads are still running
    }

    /**
     * Checks bus-source command-line argument parsing.
     */
    private static void argumentChecks() {
        check(!BusSource.fromArguments(List.of()).isRealBus(), "no arguments means the stand-in");
        check(!BusSource.fromArguments(List.of("--other", "x")).isRealBus(), "unknown arguments are ignored");
        BusSource real = BusSource.fromArguments(List.of("--bus=localhost:6100"));
        check(real.isRealBus() && real.describe().contains("localhost:6100"), "--bus=localhost:6100 means the real bus");
        check(BusSource.fromArguments(List.of("--bus=192.168.1.20:7000")).describe().contains("192.168.1.20:7000"),
                "an IP address works as the host");

        //Team03's README form: host then port as two plain arguments
        BusSource plain = BusSource.fromArguments(List.of("192.168.8.10", "6000"));
        check(plain.isRealBus() && plain.describe().contains("192.168.8.10:6000"), "two plain arguments (host port) mean the real bus");
        check(!BusSource.fromArguments(List.of("192.168.8.10")).isRealBus(), "a single plain argument is ignored");
        check(!BusSource.fromArguments(List.of("--a", "--b")).isRealBus(), "two options that are not --bus are ignored");
        checkRejects(() -> BusSource.fromArguments(List.of("localhost", "abc")), "plain arguments with a port that is not a number rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("localhost", "0")), "plain arguments with port 0 rejected");

        checkRejects(() -> BusSource.fromArguments(List.of("--bus=")), "--bus= with nothing rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=localhost")), "no port rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=:6100")), "no host rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=localhost:")), "empty port rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=localhost:abc")), "port that is not a number rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=localhost:0")), "port 0 rejected");
        checkRejects(() -> BusSource.fromArguments(List.of("--bus=localhost:70000")), "port 70000 rejected");
    }

    /**
     * Checks selection and behavior of the in-memory bus stand-in.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void standInChecks() throws Exception {
        BusSource source = new BusSource();
        BusMessageDispatcher a = source.open();
        BusMessageDispatcher b = source.open();
        b.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);
        a.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 4));
        Optional<Message> got = b.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);
        check(got.isPresent() && got.get().payload(1) == 4, "two stand-in connections from one source talk to each other");
        source.close();    //nothing to close, must not fail
    }

    /**
     * Checks connection and communication with a real software bus.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void realBusChecks() throws Exception {
        Thread busThread = new Thread(() -> {
            try {
                new SoftwareBusServer(PORT, new MessageBus()).start();
            } catch (IOException e) {
                System.out.println("The bus could not start on port " + PORT + ": " + e);
            }
        }, "software-bus");
        busThread.setDaemon(true);
        busThread.start();

        BusSource source = BusSource.fromArguments(List.of("--bus=localhost:" + PORT));
        BusMessageDispatcher car = source.open();
        BusMessageDispatcher center = source.open();
        check(car instanceof SocketBusDispatcher && center instanceof SocketBusDispatcher,
                "the real bus source opens socket connections");
        check(((SocketBusDispatcher) car).getId() >= 1 && ((SocketBusDispatcher) center).getId() >= 1
                        && ((SocketBusDispatcher) car).getId() != ((SocketBusDispatcher) center).getId(),
                "open() registers every connection, each with its own ID");

        car.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_2);
        center.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_2, 7));
        Optional<Message> got = Optional.empty();
        for (int i = 0; i < 600 && got.isEmpty(); i++) {
            got = car.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_2);
            Thread.sleep(5);
        }
        check(got.isPresent() && got.get().payload(1) == 7, "two connections from one real source talk to each other");

        source.close();
        check(!((SocketBusDispatcher) car).isConnected() && !((SocketBusDispatcher) center).isConnected(),
                "close() closes every connection that was opened");

        BusSource missing = BusSource.fromArguments(List.of("--bus=localhost:6198")).retries(2);
        boolean failed = false;
        String message = "";
        try {
            missing.open();
        } catch (IOException e) {
            failed = true;
            message = e.getMessage();
        }
        check(failed, "open() fails when no bus is listening");
        check(message.contains("localhost:6198") && message.contains("is it running"),
                "the error names the address and suggests the bus is not running: " + message);
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param message description printed when the check fails
     */
    private static void check(boolean condition, String message) {
        checksRun++;
        if (!condition) {
            checksFailed++;
            System.out.println("FAIL: " + message);
        }
    }

    /**
     * Checks rejects and reports a failure when the expectation is not met.
     *
     * @param action operation expected to throw an exception
     * @param message description printed when the check fails
     */
    private static void checkRejects(Runnable action, String message) {
        boolean threw = false;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, message);
    }
}