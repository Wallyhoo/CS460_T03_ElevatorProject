package T04ElevatorMotion.src.checks;

import SoftwareBus.MessageBus;
import SoftwareBus.SoftwareBusServer;
import Utilities.Message;
import bus.BusCabinGateway;
import bus.BusCodes;
import bus.SocketBusDispatcher;
import manager.TripController;
import sim.MotionEngine;

import java.io.IOException;
import java.util.Optional;

/**
 * Checks SocketBusDispatcher against Team03's real Software Bus (their SoftwareBusServer, started here).
 * Port 6100 is the port their README gives to tests. The bus will print its own testing messages.
 */
public class SocketBusChecks {

    private static final String HOST = "localhost";
    private static final int PORT = 6100;

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) throws Exception {
        startBus();

        registerChecks();
        deliveryChecks();
        wildcardChecks();
        orderAndUnsubscribeChecks();
        failureChecks();
        gatewayOverRealBusChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        System.exit(checksFailed > 0 ? 1 : 0);      //exit explicitly, the bus threads are still running
    }

    /** Starts bus. */
    private static void startBus() {
        Thread busThread = new Thread(() -> {
            try {
                new SoftwareBusServer(PORT, new MessageBus()).start();
            } catch (IOException e) {
                System.out.println("The bus could not start on port " + PORT + " (is another bus already running?): " + e);
            }
        }, "software-bus");
        busThread.setDaemon(true);
        busThread.start();
    }

    /**
     * Creates and returns a connection to the selected bus implementation.
     *
     * @return the connected socket dispatcher
     * @throws IOException if the operation cannot be completed
     */
    private static SocketBusDispatcher connect() throws IOException {
        return SocketBusDispatcher.connect(HOST, PORT, 50);
    }

    /**
     * Checks registration and assignment of client identifiers.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void registerChecks() throws Exception {
        try (SocketBusDispatcher a = connect(); SocketBusDispatcher b = connect()) {
            check(a.getId() == 0, "the ID is 0 before register()");
            a.register();
            b.register();
            check(a.getId() >= 1 && b.getId() >= 1, "register() gives an ID of 1 or more");
            check(a.getId() != b.getId(), "two connections get different IDs");
            int first = a.getId();
            a.register();
            check(a.getId() == first, "registering twice keeps the same ID");
            check(a.isConnected() && a.getFailure() == null, "the connection is healthy");
        }
    }

    /**
     * Checks delivery of published messages to matching subscribers.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void deliveryChecks() throws Exception {
        try (SocketBusDispatcher car = connect(); SocketBusDispatcher center = connect()) {
            car.register();
            center.register();
            car.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);

            center.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 5));
            Optional<Message> call = await(car, BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);
            check(call.isPresent(), "a published message reaches the subscriber");
            check(call.isPresent() && call.get().payload(1) == 5, "payload byte 1 (the floor) survives the bus");
            check(call.isPresent() && call.get().topic() == BusCodes.CABIN_CALL
                    && call.get().subtopic() == BusCodes.ELEVATOR_1, "topic and subtopic survive the bus");
            check(car.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1).isEmpty(), "get removes the message");

            //The bus does not give a connection its own messages back
            center.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);
            center.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 6));
            check(await(car, BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1).isPresent(), "the other connection still gets it");
            pause(200);
            check(center.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1).isEmpty(), "the publisher does not get its own message");

            //A topic nobody subscribed to
            center.publish(Message.ofPayload(BusCodes.GOTO, BusCodes.ELEVATOR_1, 7));
            pause(200);
            check(car.get(BusCodes.GOTO, BusCodes.ELEVATOR_1).isEmpty(), "no message without a subscription");
            check(car.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_2).isEmpty(), "get on a topic never subscribed is empty");
        }
    }

    /**
     * Checks wildcard topic/subtopic delivery behavior.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void wildcardChecks() throws Exception {
        try (SocketBusDispatcher listener = connect(); SocketBusDispatcher sender = connect()) {
            listener.register();
            sender.register();
            listener.subscribe(BusCodes.CABIN_STATUS, BusCodes.ALL);
            listener.subscribe(BusCodes.FIRE, BusCodes.ALL);
            listener.subscribe(BusCodes.ALIGN, BusCodes.ELEVATOR_2);

            sender.publish(Message.ofPayload(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_3, 1, 4, 0));
            Optional<Message> status = await(listener, BusCodes.CABIN_STATUS, BusCodes.ALL);
            check(status.isPresent() && status.get().payload(2) == 4,
                    "a subscription on subtopic 0 gets a message for elevator 3");

            sender.publish(Message.ofPayload(BusCodes.FIRE, BusCodes.ALL, 1));
            Optional<Message> fire = await(listener, BusCodes.FIRE, BusCodes.ALL);
            check(fire.isPresent() && fire.get().flag(1), "a building-wide FIRE message (subtopic 0) arrives");

            //A message published with subtopic 0 reaches a subscription for one elevator
            sender.publish(Message.ofPayload(BusCodes.ALIGN, BusCodes.ALL, BusCodes.ALIGN_TOP));
            Optional<Message> align = await(listener, BusCodes.ALIGN, BusCodes.ELEVATOR_2);
            check(align.isPresent() && align.get().payload(1) == BusCodes.ALIGN_TOP,
                    "a message published with subtopic 0 reaches an elevator 2 subscription");
        }
    }

    /**
     * Checks message order and unsubscribe behavior.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void orderAndUnsubscribeChecks() throws Exception {
        try (SocketBusDispatcher listener = connect(); SocketBusDispatcher sender = connect()) {
            listener.register();
            sender.register();
            listener.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4);

            for (int floor = 1; floor <= 10; floor++) {
                sender.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4, floor));
            }
            boolean inOrder = true;
            for (int floor = 1; floor <= 10; floor++) {
                Optional<Message> next = await(listener, BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4);
                if (next.isEmpty() || next.get().payload(1) != floor) {
                    inOrder = false;
                }
            }
            check(inOrder, "ten messages arrive in the order they were sent");

            listener.unsubscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4);
            sender.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4, 3));
            pause(200);
            check(listener.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_4).isEmpty(), "nothing arrives after unsubscribing");

            //Same input rules as BusStandIn
            checkRejects(() -> listener.subscribe(0, 1), "subscribe topic 0 rejected");
            checkRejects(() -> listener.subscribe(BusCodes.FIRST_COMMAND, 1), "subscribe a command-range topic rejected");
            checkRejects(() -> listener.get(5, 256), "get subtopic 256 rejected");
            checkRejects(() -> listener.publish(null), "publish null rejected");
            checkRejects(() -> listener.publish(Message.of(BusCodes.FIRST_COMMAND, 1, 1)), "publish a command-range topic rejected");
        }
    }

    /**
     * Checks dispatcher behavior when the socket connection fails.
     *
     * @throws Exception if the operation cannot be completed
     */
    private static void failureChecks() throws Exception {
        //Nothing is listening on this port
        boolean refused = false;
        try {
            SocketBusDispatcher.connect(HOST, 6199, 2);
        } catch (IOException e) {
            refused = true;
        }
        check(refused, "connecting to a port with no bus fails with an IOException");

        //A closed dispatcher goes quiet instead of throwing
        SocketBusDispatcher closing = connect();
        closing.register();
        closing.subscribe(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1);
        closing.close();
        check(!closing.isConnected(), "a closed dispatcher is not connected");
        closing.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 2));
        check(closing.get(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1).isEmpty(), "a closed dispatcher returns nothing");
    }

    /**
     * A whole cabin on the real bus: a call comes in, the car travels, and CABIN_STATUS comes back
     * @throws Exception if the operation cannot be completed
     */
    private static void gatewayOverRealBusChecks() throws Exception {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        try (SocketBusDispatcher carBus = connect(); SocketBusDispatcher center = connect()) {
            center.register();
            center.subscribe(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_1);
            center.subscribe(BusCodes.ALIGN, BusCodes.ELEVATOR_1);
            BusCabinGateway gateway = new BusCabinGateway(engine, trip, carBus, BusCodes.ELEVATOR_1);

            boolean heardStart = false;
            for (int tick = 0; tick < 500 && !heardStart; tick++) {
                gateway.update();
                trip.controlStep();
                engine.tick();
                pause(1);
                heardStart = center.get(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_1).isPresent();
            }
            check(heardStart, "the cabin announces it is stopped at floor 1 over the real bus");

            center.publish(Message.ofPayload(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 3));
            int arrivedAt = 0;
            for (int tick = 0; tick < 3000 && arrivedAt == 0; tick++) {
                gateway.update();
                trip.controlStep();
                engine.tick();
                pause(1);
                Optional<Message> status = center.get(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_1);
                if (status.isPresent() && status.get().flag(1)) {
                    arrivedAt = status.get().payload(2);
                }
            }
            check(arrivedAt == 3, "a call for floor 3 over the real bus ends with a stopped status for floor 3");
            check(engine.isFloorAligned(3), "the car is really at floor 3");
            check(carBus.isConnected(), "the car's connection stayed healthy");
        }
    }

    /**
     * Polls get() until a message comes or about 3 s pass
     *
     * @param d value for d
     * @param topic topic number
     * @param subtopic subtopic number
     * @return the resulting Optional value
     * @throws InterruptedException if the operation cannot be completed
     */
    private static Optional<Message> await(SocketBusDispatcher d, int topic, int subtopic) throws InterruptedException {
        for (int i = 0; i < 600; i++) {
            Optional<Message> m = d.get(topic, subtopic);
            if (m.isPresent()) {
                return m;
            }
            Thread.sleep(5);
        }
        return Optional.empty();
    }

    /**
     * Waits for the supplied duration in milliseconds.
     *
     * @param ms duration in milliseconds
     * @throws InterruptedException if the operation cannot be completed
     */
    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
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