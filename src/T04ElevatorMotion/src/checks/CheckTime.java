package T04ElevatorMotion.src.checks;

import sim.MotionEngine;

/**
 * Tick counts for the checks that do not depend on the default settings. A check that needs
 * "enough time for a trip" asks for it here in simulated seconds or as a count that was right
 * for the defaults (4 ft/s, 14 ft floors, 2 ft/s^2, 0.05 s steps), and gets a count that is
 * still long enough when the speed, the floor height or the acceleration is changed.
 */
public final class CheckTime {

    private CheckTime() {}

    /** Seconds one floor takes at the default settings: 14 / 4 + 4 / 2. */
    private static final double DEFAULT_SECONDS_PER_FLOOR = 14.0 / 4.0 + 4.0 / 2.0;

    /** Number of simulation steps in this many simulated seconds. */
    public static int ticksFor(double seconds) {
        return (int) Math.ceil(seconds / MotionEngine.TIME_STEP);
    }

    /** How many times longer than the defaults one floor takes now (1 or more). */
    public static double slowness() {
        double secondsPerFloor = MotionEngine.FLOOR_HEIGHT / MotionEngine.CRUISE_SPEED
                + MotionEngine.CRUISE_SPEED / MotionEngine.ACCELERATION;
        return Math.max(1.0, secondsPerFloor / DEFAULT_SECONDS_PER_FLOOR);
    }

    /** A tick count that was right for the defaults, stretched to cover the current settings. */
    public static int scaled(int ticksAtDefaults) {
        return ticksFor(ticksAtDefaults * 0.05 * slowness());
    }

    /** Enough steps for a car with no brakes to run the whole shaft from one end to a lever. */
    public static int runaway() {
        double shaft = MotionEngine.SHAFT_TOP + 2 * MotionEngine.LEVER_MARGIN;
        return ticksFor(1.5 * (shaft / MotionEngine.CRUISE_SPEED + MotionEngine.CRUISE_SPEED / MotionEngine.ACCELERATION) + 5.0);
    }
}