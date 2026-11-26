package T04ElevatorMotion.src.checks;

import manager.SimulationManager;
import manager.TripController;
import sim.MotionEngine;

/**
 * Checks simulation-manager scheduling and time scaling.
 */
public class SimulationManagerChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        MotionEngine engine = new MotionEngine();
        StringBuilder log = new StringBuilder();
        SimulationManager manager = new SimulationManager(new TripController(engine), () -> log.append("T"));
        manager.setBeforeStep(() -> log.append("H"));

        long frame = (long) Math.ceil(MotionEngine.TIME_STEP * 1e9);   //one step, rounded up to a whole nanosecond
        manager.update(0);                   //first call only records the time
        check(log.length() == 0, "no step on the first update");
        manager.update(frame);
        manager.update(2 * frame);
        manager.update(3 * frame);
        check(log.toString().equals("HTHTHT"), "the hook runs before the tick, once per step");

        checkRejects(() -> manager.setBeforeStep(null), "null hook rejected");

        //The following is meant to test the speed scale.

        StringBuilder fastLog = new StringBuilder();
        SimulationManager fast = new SimulationManager(new TripController(new MotionEngine()), () -> fastLog.append("T"));
        fast.setTimeScale(2.0);
        fast.update(0);
        fast.update(frame);
        check(fastLog.toString().equals("TT"), "0.05 s frame\" to \"step-sized frame");

        StringBuilder slowLog = new StringBuilder();
        SimulationManager slow = new SimulationManager(new TripController(new MotionEngine()), () -> slowLog.append("T"));
        slow.setTimeScale(0.5);
        slow.update(0);
        slow.update(frame);
        check(slowLog.length() == 0, "at 0.5x, one frame runs no step yet");
        slow.update(2 * frame);
        check(slowLog.toString().equals("T"), "at 0.5x, two frames run one step");

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
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