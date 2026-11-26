package T04ElevatorMotion.src.sim;

import sim.MotionEngine;

/**
 * Floor sensor logic (MSM Section 3.6). Every floor has its own sensor zone, 0.5 ft tall
 * and centered on floor level (+/- 0.25 ft). The zone is taller than one step at cruising
 * speed (0.2 ft), so the car cannot jump over it between two updates.
 * Positions are in feet along the shaft: floor 1 is 0 ft and floor 10 is 126 ft.
 */
public final class FloorSensors {

    public static final double ZONE_HEIGHT = 0.5;   // ft
    private static final double EPS = 1e-9;

    private FloorSensors() {
    }

    /** True while the car is inside the sensor zone of the given floor (1 to 10).
     *
     * @param position cabin position in feet
     * @param floor floor number
     * @return whether the requested condition is true
     */
    public static boolean isAligned(double position, int floor) {
        double offset = Math.abs(position - MotionEngine.floorPosition(floor));
        return offset <= ZONE_HEIGHT / 2.0 + EPS;
    }

    /** Floor whose sensor zone contains the car, or 0 if the car is between zones.
     *
     * @param position cabin position in feet
     * @return the resulting integer value
     */
    public static int currentFloor(double position) {
        for (int f = 1; f <= MotionEngine.FLOORS; f++) {
            if (isAligned(position, f)) {
                return f;
            }
        }
        return 0;
    }
}