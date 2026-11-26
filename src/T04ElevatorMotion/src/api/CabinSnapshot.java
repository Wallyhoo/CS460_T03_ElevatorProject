package T04ElevatorMotion.src.api;

/** One consistent reading of the cabin, taken under a single lock.
 *
 * @param position cabin position in feet
 * @param velocity cabin velocity in feet per second
 * @param direction direction of travel
 * @param state current motion state
 * @param alignedFloor floor number whose alignment sensors are active or the no-floor value
 * @param topLimitTripped whether the top safety limit is tripped
 * @param bottomLimitTripped whether the bottom safety limit is tripped
 */
public record CabinSnapshot(double position, double velocity, Direction direction,
                            MotionState state, int alignedFloor,
                            boolean topLimitTripped, boolean bottomLimitTripped) {}
