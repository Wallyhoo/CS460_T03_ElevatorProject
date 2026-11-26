package T04ElevatorMotion.src.checks;

import Utilities.Message;
import bus.BusCabinGateway;
import bus.BusCodes;
import bus.BusMessageDispatcher;
import bus.BusStandIn;
import harness.EveryTwoHarness;
import manager.TripController;
import sim.MotionEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checks the every-two-floors demonstration harness.
 */
public class EveryTwoHarnessChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        everyTwoFloorsChecks();

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Runs the every two floors checks.
     */
    private static void everyTwoFloorsChecks() {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        BusStandIn bus = new BusStandIn();
        BusMessageDispatcher monitor = bus.connect();
        monitor.subscribe(BusCodes.CABIN_STATUS, BusCodes.ELEVATOR_1);
        BusCabinGateway gateway = new BusCabinGateway(engine, trip, bus.connect(), BusCodes.ELEVATOR_1);
        EveryTwoHarness scenario = new EveryTwoHarness(bus.connect());

        List<Integer> stops = new ArrayList<>();       //floor the car was at each time it stopped
        int ticks = 0;
        while (scenario.isFinished() == false && ticks < 40000) {
            scenario.update();
            gateway.update();
            trip.controlStep();
            engine.tick();
            ticks += 1;

            //CABIN_STATUS is only published when the car stops. The very first one is the start-up announcement.
            for (Message status : drain(monitor, BusCodes.CABIN_STATUS)) {
                if (status.flag(1) && ticks > 1) {
                    stops.add(status.payload(2));
                }
            }
        }

        check(scenario.isFinished(), "Every two floors: finished before the tick limit");
        check(scenario.getArrivals().equals(List.of(3, 5, 7, 9)),
                "Every two floors: arrivals " + scenario.getArrivals());
        check(stops.equals(List.of(3, 5, 7, 9)),
                "Every two floors: the car only stopped at 3, 5, 7 and 9 " + stops);
        check(Math.abs(engine.getPosition() - MotionEngine.floorPosition(9)) < 0.11,
                "Every two floors: car ends at floor 9");
        check(engine.isTopLimitTripped() == false && engine.isBottomLimitTripped() == false,
                "Every two floors: no safety lever was engaged");
    }

    /**
     * Removes and returns queued messages for the requested topic.
     *
     * @param monitor dispatcher subscribed to the messages being observed
     * @param topic topic number
     * @return the resulting List value
     */
    private static List<Message> drain(BusMessageDispatcher monitor, int topic) {
        List<Message> values = new ArrayList<>();
        Optional<Message> next = monitor.get(topic, BusCodes.ELEVATOR_1);
        while (next.isPresent()) {
            values.add(next.get());
            next = monitor.get(topic, BusCodes.ELEVATOR_1);
        }
        return values;
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
}