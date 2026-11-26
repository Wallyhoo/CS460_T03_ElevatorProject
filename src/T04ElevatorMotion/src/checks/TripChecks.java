package T04ElevatorMotion.src.checks;

import api.MotionControllerAPI;
import api.MotionState;
import manager.TripController;
import sim.MotionEngine;

/**
 * Checks trip-controller behavior, including normal trips and safety edge cases.
 */
public class TripChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        MotionEngine engine = new MotionEngine();
        TripController controller = new TripController(engine);
        Runnable tick = engine::tick;

        int[] route = {2, 10, 4, 1, 10, 1, 5, 6, 3};
        for (int floor : route) {
            verifyTrip(engine, controller, tick, floor);
        }

        leverChecks();
        edgeCases(engine, tick);

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /** Sends the car to a floor and ticks until the trip ends or the cap is hit.
     *
     * @param controller trip controller used by the harness
     * @param api value for api
     * @param tick simulation tick callback
     * @param floor floor number
     * @param maxTicks value for maxTicks
     * @param reads value for reads
     * @return whether the requested condition is true
     */
    private static boolean runTrip(TripController controller, MotionControllerAPI api, Runnable tick,
                                   int floor, int maxTicks, int[] reads) {
        controller.goToFloor(floor);
        int ticks = 0;
        while (controller.isTripActive() && ticks < maxTicks) {
            controller.controlStep();
            tick.run();
            if (reads != null) {
                for (int f = 1; f <= MotionEngine.FLOORS; f++) {
                    if (api.isFloorAligned(f)) {
                        reads[f]++;
                    }
                }
            }
            ticks++;
        }
        return !controller.isTripActive();
    }

    /** Records one check and reports it if it fails.
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
     * Checks the expected state after a trip.
     *
     * @param engine motion engine being checked
     * @param controller trip controller used by the harness
     * @param tick simulation tick callback
     * @param floor floor number
     */
    private static void verifyTrip(MotionEngine engine, TripController controller,
                                   Runnable tick, int floor) {

        String label = "trip to floor " + floor + ": ";
        int[] reads = new int[MotionEngine.FLOORS + 1];
        double startPos = engine.getPosition();
        boolean finished = runTrip(controller, engine, tick, floor, CheckTime.scaled(5000), reads);
        check(finished, label + "finishes");
        check(engine.getState() == MotionState.STOPPED, label + "state is STOPPED");
        check(!engine.isMoving(), label + "not moving");
        check(Math.abs(engine.getPosition() - MotionEngine.floorPosition(floor)) <= TripController.levelTolerance + 1e-9,
                label + "position within the level tolerance of the floor");
        check(engine.isFloorAligned(floor), label + "aligned with target floor");
        for (int other = 1; other <= MotionEngine.FLOORS; other++) {
            if (other != floor) {
                check(!engine.isFloorAligned(other), label + "not aligned with floor " + other);
            }
        }

        double target = MotionEngine.floorPosition(floor);
        double lowPos = Math.min(startPos, target);
        double highPos = Math.max(startPos, target);
        for (int f = 1; f <= MotionEngine.FLOORS; f++) {
            double p = MotionEngine.floorPosition(f);
            double slack = TripController.levelTolerance + 1e-9;   //the car starts and ends within the tolerance of a floor
            if (p >= lowPos - slack && p <= highPos + slack) {
                check(reads[f] >= 2, label + "floor " + f + " sensor read at least twice (read " + reads[f] + ")");
            } else {
                check(reads[f] == 0, label + "floor " + f + " sensor never read outside the trip");
            }
        }
        check(engine.isTopAligned() == (floor == MotionEngine.FLOORS), label + "top sensor matches the floor");
        check(engine.isBottomAligned() == (floor == 1), label + "bottom sensor matches the floor");
    }

    /**
     * Checks behavior at the safety-lever limits.
     */
    private static void leverChecks() {
        MotionEngine engine = new MotionEngine();
        TripController trip = new TripController(engine);
        trip.goToFloor(10);
        int guard = 0;
        while (engine.isMoving() && guard++ < CheckTime.runaway()) {
            engine.tick();                    // no controlStep(), so nothing brakes
        }
        check(engine.isTopLimitTripped(), "a runaway car reaches the top lever");
        check(trip.isTripActive(), "the trip is still marked active before controlStep");
        trip.controlStep();
        check(trip.isTripActive() == false, "controlStep abandons the trip when a lever has tripped");
        trip.goToFloor(5);
        check(trip.isTripActive() == false, "goToFloor is refused while locked out");
        double frozenAt = engine.getPosition();
        for (int i = 0; i < 20; i++) {
            trip.controlStep();
            engine.tick();
        }
        check(engine.getPosition() == frozenAt, "the car stays frozen");
    }

    /**
     * Checks motion-engine edge cases using the supplied engine and tick callback.
     *
     * @param engine motion engine being checked
     * @param tick simulation tick callback
     */
    private static void edgeCases(MotionEngine engine, Runnable tick) {
        TripController controller = new TripController(engine);

        // 1. Requesting the floor we're already on does nothing
        runTrip(controller, engine, tick, 3, CheckTime.scaled(5000), null);
        double before = engine.getPosition();
        controller.goToFloor(3);
        check(!controller.isTripActive(), "same-floor request starts no trip");
        check(engine.getPosition() == before, "same-floor request does not move the car");

        // 2. Invalid floors are rejected
        controller.goToFloor(0);
        check(!controller.isTripActive(), "floor 0 rejected");
        controller.goToFloor(MotionEngine.FLOORS + 1);
        check(!controller.isTripActive(), "floor above the top rejected");

        // 3. A request while moving is ignored and the first trip still completes
        controller.goToFloor(8);
        for (int i = 0; i < 40; i++) {
            controller.controlStep();
            tick.run();
        }
        check(controller.isTripActive(), "trip to 8 still under way");
        controller.goToFloor(2);
        int ticks = 0;
        while (controller.isTripActive() && ticks < CheckTime.scaled(5000)) {
            controller.controlStep();
            tick.run();
            ticks++;
        }
        check(engine.isFloorAligned(8), "second request ignored, car ends at floor 8");

        // 4. Start a trip from a position that is not a floor: halfway between floors 3 and 4
        MotionEngine between = new MotionEngine(MotionEngine.floorPosition(3) + MotionEngine.FLOOR_HEIGHT / 2.0);
        check(between.getCurrentFloor() == 0, "the start position is between floor zones");
        verifyTrip(between, new TripController(between), between::tick, 6);
    }
}