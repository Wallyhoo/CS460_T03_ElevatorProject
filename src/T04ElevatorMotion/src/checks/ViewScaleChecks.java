package T04ElevatorMotion.src.checks;

import api.ShaftSpec;
import gui.ViewScale;
import sim.MotionEngine;

/** Checks that the GUI's drawing math agrees with the engine, whatever the floor height is. */
public class ViewScaleChecks {

    private static int checksRun = 0;
    private static int checksFailed = 0;

    public static void main(String[] args) {
        ShaftSpec spec = new ShaftSpec(MotionEngine.FLOORS, MotionEngine.FLOOR_HEIGHT, MotionEngine.LEVER_MARGIN,
                MotionEngine.CRUISE_SPEED, MotionEngine.ACCELERATION, MotionEngine.DECELERATION);
        ViewScale scale = new ViewScale(spec);

        for (int floor = 1; floor <= MotionEngine.FLOORS; floor++) {
            check(Math.abs(scale.floorToFeet(floor) - MotionEngine.floorPosition(floor)) < 1e-9,
                    "floor " + floor + " is drawn where the engine puts it");
        }
        double spacing = scale.feetToWorldYValue(scale.floorToFeet(1)) - scale.feetToWorldYValue(scale.floorToFeet(2));
        check(Math.abs(spacing - MotionEngine.FLOOR_HEIGHT * ViewScale.pixelsPerFoot) < 1e-9,
                "neighbouring floors are one floor height apart on screen");

        double top = scale.feetToWorldYValue(MotionEngine.MAX_POSITION);
        double bottom = scale.feetToWorldYValue(MotionEngine.MIN_POSITION);
        check(top >= 0, "the top of the shaft is inside the drawing");
        check(bottom <= scale.shaftHeight(), "the bottom of the shaft is inside the drawing");
        check(scale.feetToWorldYValue(MotionEngine.SHAFT_TOP + scale.leverMargin()) >= 0,
                "the top lever is inside the drawing");
        check(scale.feetToWorldYValue(-scale.leverMargin()) <= scale.shaftHeight(),
                "the bottom lever is inside the drawing");

        boolean rejected = false;
        try {
            new ShaftSpec(1, 14.0, 1.0, 4.0, 2.0, 2.0);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "a one-floor shaft is rejected");

        System.out.println(checksRun + " checks run, " + checksFailed + " failed");
        if (checksFailed > 0) {
            System.exit(1);
        }
    }

    /**
     * Asserts that the supplied condition is true and reports the message if it is false.
     *
     * @param condition condition that must be true for the check to pass
     * @param what description printed when the check fails
     */
    private static void check(boolean condition, String what) {
        checksRun++;
        if (!condition) {
            checksFailed++;
            System.out.println("FAIL: " + what);
        }
    }
}