package T04ElevatorMotion.src.checks;

import Utilities.Message;
import bus.BusCodes;
import bus.BusMessageDispatcher;
import bus.BusStandIn;
import java.util.Optional;

/**
 * Checks message delivery, ordering, wildcard matching, and queue behavior in the stand-in.
 */
public class BusStandInChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        deliveryChecks();
        orderChecks();
        subtopicChecks();
        unsubscribeChecks();
        queueLimitChecks();
        collisionChecks();
        badInputChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Checks delivery of published messages to matching subscribers.
     */
    private static void deliveryChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();
        int topic = BusCodes.CABIN_STATUS;
        int elevator = BusCodes.ELEVATOR_1;

        listener.subscribe(topic, elevator);
        sender.publish(Message.of(topic, elevator, 1));
        check(holdsValue(listener.get(topic, elevator), 1), "subscriber receives a published message");
        check(listener.get(topic, elevator).isEmpty(), "get removes the message");

        sender.subscribe(topic, elevator);
        sender.publish(Message.of(topic, elevator, 2));
        check(sender.get(topic, elevator).isEmpty(), "publisher does not receive its own message");
        check(holdsValue(listener.get(topic, elevator), 2), "the other connection still receives it");

        sender.publish(Message.of(BusCodes.ALIGN, elevator, 5));
        check(listener.get(BusCodes.ALIGN, elevator).isEmpty(), "no message without a subscription");

        BusMessageDispatcher second = bus.connect();
        second.subscribe(topic, elevator);
        sender.publish(Message.of(topic, elevator, 3));
        check(holdsValue(listener.get(topic, elevator), 3), "first listener gets the message");
        check(holdsValue(second.get(topic, elevator), 3), "second listener gets the message");
    }

    /**
     * Checks that queued messages are delivered in publication order.
     */
    private static void orderChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();
        int topic = BusCodes.CABIN_CALL;
        int elevator = BusCodes.ELEVATOR_1;

        listener.subscribe(topic, elevator);
        for (int i = 1; i <= 3; i++) {
            sender.publish(Message.of(topic, elevator, i));
        }
        check(holdsValue(listener.get(topic, elevator), 1), "the oldest message comes first");
        check(holdsValue(listener.get(topic, elevator), 2), "the second message comes second");
        check(holdsValue(listener.get(topic, elevator), 3), "the third message comes third");
        check(listener.get(topic, elevator).isEmpty(), "the queue is empty once everything is taken");
    }

    /**
     * Checks that distinct topic/subtopic pairs remain distinct.
     */
    private static void collisionChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();

        listener.subscribe(1, 2);
        listener.subscribe(2, 1);
        sender.publish(Message.of(1, 2, 100));
        check(listener.get(2, 1).isEmpty(), "a message for (1, 2) does not appear in the (2, 1) queue");
        check(holdsValue(listener.get(1, 2), 100), "a message for (1, 2) is in the (1, 2) queue");

        listener.subscribe(1, 0);
        listener.subscribe(2, 0);
        sender.publish(Message.of(1, 0, 7));
        check(listener.get(2, 0).isEmpty(), "a message for (1, 0) does not appear in the (2, 0) queue");
        check(holdsValue(listener.get(1, 0), 7), "a message for (1, 0) is in the (1, 0) queue");
    }

    /**
     * Checks rejection of invalid input values.
     */
    private static void badInputChecks() {
        BusMessageDispatcher conn = new BusStandIn().connect();

        checkRejects(() -> conn.subscribe(0, 1), "subscribe topic 0 rejected");
        checkRejects(() -> conn.subscribe(BusCodes.FIRST_COMMAND, 1), "subscribe a command-range topic rejected");
        checkRejects(() -> conn.subscribe(5, 256), "subscribe subtopic 256 rejected");
        checkRejects(() -> conn.subscribe(5, -1), "subscribe subtopic -1 rejected");
        checkAccepts(() -> conn.subscribe(5, 0), "subscribe with subtopic 0 (all elevators) accepted");
        checkRejects(() -> conn.unsubscribe(0, 1), "unsubscribe topic 0 rejected");
        checkRejects(() -> conn.publish(null), "publish null rejected");
        checkRejects(() -> conn.publish(Message.of(0, 1, 1)), "publish topic 0 rejected");
        checkRejects(() -> conn.publish(Message.of(BusCodes.FIRST_COMMAND, 1, 1)), "publish a command-range topic rejected");
        checkRejects(() -> conn.get(300, 0), "get topic 300 rejected");
        checkRejects(() -> conn.get(5, 256), "get subtopic 256 rejected");
    }

    /**
     * Checks exact and wildcard subtopic matching.
     */
    private static void subtopicChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();
        int topic = BusCodes.CABIN_CALL;

        listener.subscribe(topic, BusCodes.ALL);
        listener.subscribe(topic, BusCodes.ELEVATOR_1);
        listener.subscribe(topic, BusCodes.ELEVATOR_2);

        sender.publish(Message.of(topic, BusCodes.ELEVATOR_1, 5));
        check(holdsValue(listener.get(topic, BusCodes.ELEVATOR_1), 5), "a message for elevator 1 reaches the elevator 1 queue");
        check(listener.get(topic, BusCodes.ELEVATOR_2).isEmpty(), "a message for elevator 1 does not reach the elevator 2 queue");
        check(holdsValue(listener.get(topic, BusCodes.ALL), 5), "a subscription on subtopic 0 catches a message for elevator 1 (as on Team03's bus)");

        sender.publish(Message.of(topic, BusCodes.ELEVATOR_2, 6));
        check(holdsValue(listener.get(topic, BusCodes.ALL), 6), "a subscription on subtopic 0 catches a message for elevator 2 too");
        check(holdsValue(listener.get(topic, BusCodes.ELEVATOR_2), 6), "the elevator 2 queue also gets it");
        check(listener.get(topic, BusCodes.ELEVATOR_1).isEmpty(), "the elevator 1 queue does not get a message for elevator 2");

        sender.publish(Message.of(topic, BusCodes.ALL, 9));
        check(holdsValue(listener.get(topic, BusCodes.ELEVATOR_1), 9), "a message for all reaches the elevator 1 queue");
        check(holdsValue(listener.get(topic, BusCodes.ELEVATOR_2), 9), "a message for all reaches the elevator 2 queue");
        check(holdsValue(listener.get(topic, BusCodes.ALL), 9), "a message for all reaches the subtopic 0 queue");
    }

    /**
     * Checks that unsubscribed messages are no longer delivered.
     */
    private static void unsubscribeChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();
        int topic = BusCodes.CABIN_STATUS;
        int elevator = BusCodes.ELEVATOR_1;

        listener.subscribe(topic, elevator);
        sender.publish(Message.of(topic, elevator, 4));
        listener.unsubscribe(topic, elevator);
        check(listener.get(topic, elevator).isEmpty(), "unsubscribe discards the queued message");

        sender.publish(Message.of(topic, elevator, 6));
        check(listener.get(topic, elevator).isEmpty(), "no delivery after unsubscribing");

        listener.subscribe(topic, elevator);
        check(listener.get(topic, elevator).isEmpty(), "a new subscription starts with an empty queue");
    }

    /**
     * Checks the bounded queue behavior when more messages arrive than it can hold.
     */
    private static void queueLimitChecks() {
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher sender = bus.connect();
        BusMessageDispatcher listener = bus.connect();
        int topic = BusCodes.UP_CALL;

        listener.subscribe(topic, BusCodes.ALL);
        for (int i = 0; i < 1100; i++) {
            sender.publish(Message.of(topic, BusCodes.ALL, i));
        }
        int count = 0;
        long first = -1;
        long last = -1;
        Optional<Message> next = listener.get(topic, BusCodes.ALL);
        while (next.isPresent()) {
            if (count == 0) {
                first = next.get().value();
            }
            last = next.get().value();
            count++;
            next = listener.get(topic, BusCodes.ALL);
        }
        check(count == 1024, "a full queue holds 1024 messages");
        check(first == 1100 - 1024, "the oldest messages are dropped once the queue is full (as on Team03's bus)");
        check(last == 1099, "the newest message is kept");
    }

    /**
     * Reports whether the optional message contains the expected numeric value.
     *
     * @param message description printed when the check fails
     * @param expected expected value
     * @return whether the requested condition is true
     */
    private static boolean holdsValue(Optional<Message> message, long expected) {
        return message.isPresent() && message.get().value() == expected;
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param message description printed when the check fails
     */
    private static void check(boolean condition, String message) {
        checksRun++;
        if (condition == false) {
            checksFailed++;
            System.out.println("FAIL: " + message);
        }
    }

    /**
     * Passes only if the action throws an IllegalArgumentException.
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

    /**
     * Checks accepts and reports a failure when the expectation is not met.
     *
     * @param action operation expected to throw an exception
     * @param message description printed when the check fails
     */
    private static void checkAccepts(Runnable action, String message) {
        boolean threw = false;
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(!threw, message);
    }
}
