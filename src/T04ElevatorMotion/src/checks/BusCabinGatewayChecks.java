package T04ElevatorMotion.src.checks;

import Utilities.Message;
import api.Direction;
import bus.*;
import manager.TripController;
import sim.MotionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs checks for cabin-to-bus message translation and gateway behavior.
 */
public class BusCabinGatewayChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {

        initialAnnouncementChecks();
        callServedChecks();
        queuedCallsChecks();
        gotoAndServiceChecks();
        invalidCallChecks();
        sameFloorChecks();
        otherElevatorChecks();
        constructorChecks();
        faultChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /** One CABIN_STATUS message: P1 stopped, P2 floor, P3 direction. */
    private record Status(int stopped, int floor, int direction) {}

    /** The status of a car that has stopped at a floor. */
    private static Status stoppedAt(int floor) {
        return new Status(1, floor, BusCodes.DIR_IDLE);
    }

    /** One car, one bus, a command-center connection, and a monitor that listens to what the cabin publishes. */
    private static class Rig {
        final MotionEngine engine = new MotionEngine();
        final TripController trip = new TripController(engine);
        final BusStandIn bus = new BusStandIn();
        final BusMessageDispatcher center = bus.connect();
        final BusMessageDispatcher monitor = bus.connect();
        final BusCabinGateway gateway;

        Rig() {
            monitor.subscribe(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_1);
            monitor.subscribe(BusCodes.ALIGN, BusCodes.ELEVATOR_1);
            monitor.subscribe(BusCodes.CAR_FAULT, BusCodes.ELEVATOR_1);
            gateway = new BusCabinGateway(engine, trip, bus.connect(), BusCodes.ELEVATOR_1);
        }

        void run(int ticks) {
            for (int i = 0; i < ticks; i++) {
                gateway.update();
                trip.controlStep();
                engine.tick();
            }
        }

        /**
         * Creates or queues the requested cabin-call message.
         *
         * @param subtopic subtopic number
         * @param floor floor number
         */
        void call(int subtopic, int floor) {
            send(BusCodes.CABIN_CALL, subtopic, floor);
        }

        /**
         * Queues or sends a message to the connected router or bus endpoint.
         *
         * @param topic topic number
         * @param subtopic subtopic number
         * @param floor floor number
         */
        void send(int topic, int subtopic, int floor) {
            center.publish(Message.ofPayload(topic, subtopic, floor));
        }

        /**
         * Takes every CABIN_STATUS the monitor has queued, oldest first.
         *
         * @return the resulting List value
         */
        List<Status> statuses() {
            List<Status> found = new ArrayList<>();
            for (Message m : drain(BusCodes.CABIN_STATUS)) {
                found.add(new Status(m.payload(1), m.payload(2), m.payload(3)));
            }
            return found;
        }

        /**
         * Takes every ALIGN or CAR_FAULT message queued, and returns payload byte 1 of each.
         *
         * @param topic topic number
         * @return the resulting List value
         */
        List<Integer> firstBytes(int topic) {
            List<Integer> found = new ArrayList<>();
            for (Message m : drain(topic)) {
                found.add(m.payload(1));
            }
            return found;
        }

        /**
         * Removes and returns queued messages for the requested topic.
         *
         * @param topic topic number
         * @return the resulting List value
         */
        private List<Message> drain(int topic) {
            List<Message> messages = new ArrayList<>();
            Optional<Message> next = monitor.get(topic, BusCodes.ELEVATOR_1);
            while (next.isPresent()) {
                messages.add(next.get());
                next = monitor.get(topic, BusCodes.ELEVATOR_1);
            }
            return messages;
        }

        /**
         * Forgets everything announced so far
         */
        void clear() {
            statuses();
            firstBytes(BusCodes.ALIGN);
            firstBytes(BusCodes.CAR_FAULT);
        }
    }

    /**
     * Checks the gateway’s initial status announcements.
     */
    private static void initialAnnouncementChecks() {
        Rig rig = new Rig();
        rig.run(1);
        check(rig.statuses().equals(List.of(stoppedAt(1))), "the first update announces stopped at floor 1, direction idle");
        check(rig.firstBytes(BusCodes.ALIGN).equals(List.of(BusCodes.ALIGN_BOTTOM)),
                "the first update announces the car is aligned with the bottom of the shaft");

        rig.run(50);
        check(rig.statuses().isEmpty(), "the status is not repeated while the car sits still");
        check(rig.firstBytes(BusCodes.ALIGN).isEmpty(), "the alignment is not repeated while the car sits still");
    }

    /**
     * Checks that a cabin call is served and reported.
     */
    private static void callServedChecks() {
        Rig rig = new Rig();
        rig.run(1);
        rig.clear();

        rig.call(BusCodes.ELEVATOR_1, 5);
        rig.run(100);
        check(rig.engine.isMoving(), "the car is travelling after the call");
        check(rig.statuses().isEmpty(), "no status is published while the car travels");

        rig.run(2000);
        check(rig.engine.isFloorAligned(5), "a cabin call for floor 5 takes the car to floor 5");
        check(rig.statuses().equals(List.of(stoppedAt(5))),
                "one status is published, stopped at floor 5, and none for the floors it passed");
        check(rig.firstBytes(BusCodes.ALIGN).equals(List.of(BusCodes.ALIGN_NONE)),
                "the alignment goes to none when the car leaves floor 1 and stays none until it stops at floor 5");
    }

    /**
     * Checks that queued calls are served in order.
     */
    private static void queuedCallsChecks() {
        Rig rig = new Rig();
        rig.run(1);
        rig.clear();

        rig.call(BusCodes.ELEVATOR_1, 3);
        rig.call(BusCodes.ELEVATOR_1, 2);
        rig.call(BusCodes.ELEVATOR_1, 4);
        rig.run(4000);
        check(rig.engine.isFloorAligned(4), "three calls are served and the car ends at floor 4");
        check(rig.statuses().equals(List.of(stoppedAt(3), stoppedAt(2), stoppedAt(4))),
                "calls are served in the order they arrived (3, then 2, then 4), one status each");
    }

    /**
     * Checks GOTO and service-mode message handling.
     */
    private static void gotoAndServiceChecks() {
        Rig rig = new Rig();
        rig.run(1);
        rig.clear();

        rig.send(BusCodes.GOTO, BusCodes.ELEVATOR_1, 3);
        rig.run(2000);
        check(rig.engine.isFloorAligned(3), "GOTO takes the car to the floor");
        check(rig.statuses().equals(List.of(stoppedAt(3))), "GOTO ends with a stopped status at floor 3");

        rig.send(BusCodes.SERVICE, BusCodes.ELEVATOR_1, 2);
        rig.run(2000);
        check(rig.engine.isFloorAligned(2), "SERVICE takes the car to the floor");
        check(rig.statuses().equals(List.of(stoppedAt(2))), "SERVICE ends with a stopped status at floor 2");
    }

    /**
     * Checks that invalid floor calls are ignored or rejected.
     */
    private static void invalidCallChecks() {
        Rig rig = new Rig();
        rig.run(1);

        rig.call(BusCodes.ELEVATOR_1, 0);
        rig.call(BusCodes.ELEVATOR_1, 11);
        rig.call(BusCodes.ELEVATOR_1, 255);
        //The old number style puts the floor in payload byte 6, so payload byte 1 is 0 and the call is not a floor
        rig.center.publish(Message.of(BusCodes.CABIN_CALL, BusCodes.ELEVATOR_1, 5));
        rig.run(200);
        check(rig.engine.isFloorAligned(1), "invalid floors and old-style messages do not move the car");

        rig.call(BusCodes.ELEVATOR_1, 2);
        rig.run(2000);
        check(rig.engine.isFloorAligned(2), "invalid floors are skipped and the valid call is still served");
    }

    /**
     * Checks the behavior of a request for the current floor.
     */
    private static void sameFloorChecks() {
        Rig rig = new Rig();
        rig.run(1);
        rig.clear();

        rig.call(BusCodes.ELEVATOR_1, 1);
        rig.run(200);
        check(rig.engine.isFloorAligned(1), "a call for the current floor does not move the car");
        check(rig.statuses().isEmpty(), "a call for the current floor publishes no new status");
        check(rig.firstBytes(BusCodes.ALIGN).isEmpty(), "a call for the current floor publishes no alignment change");
    }

    /**
     * Checks that messages for another elevator are not consumed.
     */
    private static void otherElevatorChecks() {
        Rig rig = new Rig();
        rig.run(1);

        rig.call(BusCodes.ELEVATOR_2, 5);
        rig.run(200);
        check(rig.engine.isFloorAligned(1), "a call for elevator 2 is ignored by elevator 1");
    }

    /**
     * Checks constructor validation and initial state.
     */
    private static void constructorChecks() {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        BusStandIn bus = new BusStandIn();
        checkRejects(() -> new BusCabinGateway(engine, trip, bus.connect(), 0), "elevator 0 rejected");
        checkRejects(() -> new BusCabinGateway(engine, trip, bus.connect(), 5), "elevator 5 rejected");
    }

    /**
     * Checks publication of elevator fault messages.
     */
    private static void faultChecks() {
        Rig rig = new Rig();
        rig.run(1);
        rig.clear();

        rig.engine.startMotor(Direction.Up);      //runaway: no trip, so nothing brakes
        rig.run(1000);
        check(rig.engine.isTopLimitTripped(), "the runaway car trips the top lever");
        check(rig.firstBytes(BusCodes.CAR_FAULT).equals(List.of(BusCodes.FAULT_TOP_LIMIT)),
                "the top lever fault is announced once, with the fault code in payload byte 1");
        check(rig.statuses().isEmpty(), "a car stopped at a lever has no floor, so no status is published");

        rig.call(BusCodes.ELEVATOR_1, 5);
        rig.run(40);
        check(rig.firstBytes(BusCodes.CAR_FAULT).isEmpty(), "the fault is not announced twice");
        check(rig.statuses().isEmpty(), "a call is ignored while locked out");
        check(rig.engine.getPosition() == MotionEngine.MAX_POSITION, "the car stays at the lever");

        Rig low = new Rig();
        low.run(1);
        low.clear();
        low.engine.startMotor(Direction.Down);
        low.run(200);
        check(low.engine.isBottomLimitTripped(), "the runaway car trips the bottom lever");
        check(low.firstBytes(BusCodes.CAR_FAULT).equals(List.of(BusCodes.FAULT_BOTTOM_LIMIT)),
                "the bottom lever fault is announced once");
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