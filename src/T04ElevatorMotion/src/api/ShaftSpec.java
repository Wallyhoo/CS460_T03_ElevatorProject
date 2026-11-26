package T04ElevatorMotion.src.api;

/**
 * The numbers that describe a shaft and how the cabin moves in it. Whoever builds the cars
 * (Main) fills it in from the real implementation, and the GUI draws from it, so the GUI never
 * has to know which implementation it is showing.
 *
 * @param floors number of floors in the shaft
 * @param floorHeight vertical distance between adjacent floors in feet
 * @param leverMargin safety-lever margin in feet
 * @param cruiseSpeed cruise speed in feet per second
 * @param acceleration acceleration in feet per second squared
 * @param deceleration deceleration in feet per second squared
 */
public record ShaftSpec(int floors, double floorHeight, double leverMargin,
                        double cruiseSpeed, double acceleration, double deceleration) {

    /**
     * Validates the record components when a ShaftSpec instance is created.
     *
     * @throws IllegalArgumentException if the record configuration is invalid
     */
    public ShaftSpec {
        if (floors < 2) {
            throw new IllegalArgumentException("a shaft needs at least 2 floors: " + floors);
        }
        if (!(floorHeight > 0) || !(cruiseSpeed > 0) || !(acceleration > 0) || !(deceleration > 0)) {
            throw new IllegalArgumentException("floor height, cruise speed, acceleration and deceleration must be positive");
        }
        if (!(leverMargin >= 0)) {
            throw new IllegalArgumentException("lever margin must be 0 or more: " + leverMargin);
        }
    }

    /** Position of this floor in feet; floor 1 is 0 ft.
     *
     * @param floor floor number
     * @return the vertical position in feet
     */
    public double floorPosition(int floor) {
        return (floor - 1) * floorHeight;
    }
}
