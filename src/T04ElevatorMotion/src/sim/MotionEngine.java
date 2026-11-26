package T04ElevatorMotion.src.sim;

import T04ElevatorMotion.src.api.CabinSnapshot;
import T04ElevatorMotion.src.api.Direction;
import T04ElevatorMotion.src.api.MotionControllerAPI;
import T04ElevatorMotion.src.api.MotionState;

/**
 * Motion engine for one elevator car (MSM Section 3).
 * Position is measured in feet along the shaft: floor 1 is 0 ft and floor 10 is 126 ft.
 * The shaft runs 0.25 ft past each end floor (see OVERRUN).
 * Call tick() once every TIME_STEP seconds. Each tick uses the update rule from Table 3-4:
 * position = position + speed * dt, then speed = speed + acceleration * dt.
 * The engine does not know where the car is going. The controller decides when to call
 * stopMotor(): when the distance remaining is no more than brakingDistance(getVelocity()).
 */
public class MotionEngine implements MotionControllerAPI {

    public static final double ACCELERATION = 2.0;    // ft/s^2
    public static final double DECELERATION = 2.0;    // ft/s^2
    public static final double CRUISE_SPEED = 4.0;    // ft/s
    public static final double FLOOR_HEIGHT = 14.0;   // ft
    public static final int FLOORS = 10;

    /**
     * Longest simulation step, in seconds: 0.05 s, or less when the cruise speed is high.
     * One step at cruising speed must not move the car more than half a sensor zone (0.25 ft),
     * or the car could jump over a floor's sensor zone between two ticks.
     */
    public static final double TIME_STEP = Math.min(0.05, (FloorSensors.ZONE_HEIGHT / 2.0) / CRUISE_SPEED);   // s

    /** Distance moved in one update at cruising speed (0.2 ft). */
    public static final double STEP_SIZE = CRUISE_SPEED * TIME_STEP;
    /** ft moved by one stepUp/stepDn, independent of cruise speed */
    public static final double NUDGE_STEP = 0.1;
    /** Position of floor 10 (126 ft). */
    public static final double SHAFT_TOP = (FLOORS - 1) * FLOOR_HEIGHT;
    /**
     * The shaft runs this far past floor 1 and floor 10 (half the 0.5 ft sensor zone).
     * Braking can leave the car up to one step (0.2 ft) past floor level, and this room
     * lets it finish a normal deceleration instead of being cut off at the end floor.
     */
    //Normal braking can leave the car up to one step (0.2 ft) past floor level
    public static final double OVERRUN = 0.25;
    //The safety levers sit this far past floor 1 and floor 10. Normal braking never reaches them.
    public static final double LEVER_MARGIN = 1.0;
    public static final double MIN_POSITION = -LEVER_MARGIN;
    public static final double MAX_POSITION = SHAFT_TOP + LEVER_MARGIN;

    private double position;
    private double speed;
    private Direction direction = Direction.Up;
    private MotionState state = MotionState.STOPPED;

    private boolean topLimitTripped = false;
    private boolean bottomLimitTripped = false;

    public MotionEngine() {
        this(0.0);
    }

    /** startPosition is in feet, so 0 is floor 1. */
    public MotionEngine(double startPosition) {
        this.position = clamp(startPosition);
    }

    // static helpers

    /** Position of a floor in feet. Floor 1 is 0 ft. */
    public static double floorPosition(int floor) {
        if (floor < 1 || floor > FLOORS) {
            throw new IllegalArgumentException("floor must be 1 to " + FLOORS + ": " + floor);
        }
        return (floor - 1) * FLOOR_HEIGHT;
    }

    /** Distance the car needs to stop from the given speed: v^2 / (2d). */
    public static double brakingDistance(double speed) {
        return speed * speed / (2.0 * DECELERATION);
    }

    // Motion controller api

    /** Starts accelerating in the given direction. Ignored unless the car is stopped. */
    @Override
    public synchronized void startMotor(Direction dir) {
        if (state != MotionState.STOPPED || isLimitTripped()) {
            return;
        }
        direction = dir;
        state = MotionState.ACCELERATING;
    }

    /** Decelerates to a stop. Ignored if the car is already stopped or decelerating. */
    @Override
    public synchronized void stopMotor() {
        if (state == MotionState.ACCELERATING || state == MotionState.CRUISING) {
            state = MotionState.DECELERATING;
        }
    }

    /** Moves the stopped car up one step (0.2 ft). Ignored unless the car is stopped. */
    @Override
    public synchronized void stepUpMotor() {
        if (state == MotionState.STOPPED && !isLimitTripped()) {
            position = clamp(position + NUDGE_STEP);
        }
    }

    /** Moves the stopped car down one step (0.2 ft). Ignored unless the car is stopped. */
    @Override
    public synchronized void stepDnMotor() {
        if (state == MotionState.STOPPED && !isLimitTripped()) {
            position = clamp(position - NUDGE_STEP);
        }
    }

    /** Position in feet. Floor 1 is 0 ft. */
    @Override
    public synchronized double getPosition() {
        return position;
    }

    /** Speed in ft/s, never negative. Use getDirection() for the direction of travel. */
    @Override
    public synchronized double getVelocity() {
        return speed;
    }

    /** Direction of the current or most recent movement. */
    @Override
    public synchronized Direction getDirection() {
        return direction;
    }

    /** True while the car is not stopped. */
    @Override
    public synchronized boolean isMoving() {
        return state != MotionState.STOPPED;
    }

    /** True while the car is in the sensor zone of floor 10, the top of the shaft. */
    @Override
    public synchronized boolean isTopAligned() {
        return FloorSensors.isAligned(position, FLOORS);
    }

    /** True while the car is in the sensor zone of floor 1, the bottom of the shaft. */
    @Override
    public synchronized boolean isBottomAligned() {
        return FloorSensors.isAligned(position, 1);
    }

    /** True while the car is in the sensor zone of the given floor (1 to 10). */
    @Override
    public synchronized boolean isFloorAligned(int floor) {
        return FloorSensors.isAligned(position, floor);
    }

    /** True once the car has hit the safety lever above floor 10. The motor stays locked. */
    @Override
    public synchronized boolean isTopLimitTripped() {
        return topLimitTripped;
    }

    /** True once the car has hit the safety lever below floor 1. The motor stays locked. */
    @Override
    public synchronized boolean isBottomLimitTripped() {
        return bottomLimitTripped;
    }

    private boolean isLimitTripped() {
        return topLimitTripped || bottomLimitTripped;
    }

    // simulation step

    /** Advances the simulation by TIME_STEP seconds. */
    public synchronized void tick() {
        if (state == MotionState.STOPPED) {
            return;
        }
        double sign = (direction == Direction.Up) ? 1.0 : -1.0;
        position += sign * speed * TIME_STEP;

        switch (state) {
            case ACCELERATING:
                speed += ACCELERATION * TIME_STEP;
                if (speed >= CRUISE_SPEED) {
                    speed = CRUISE_SPEED;
                    state = MotionState.CRUISING;
                }
                break;
            case DECELERATING:
                speed -= DECELERATION * TIME_STEP;
                if (speed <= 1e-9) {
                    speed = 0.0;
                    state = MotionState.STOPPED;
                }
                break;
            default:
                break;
        }

        //Safety levers: hitting one stops the car at once and locks the motor.
        if (position <= MIN_POSITION && direction == Direction.Down) {
            position = MIN_POSITION;
            bottomLimitTripped = true;
            halt();
        } else if (position >= MAX_POSITION && direction == Direction.Up) {
            position = MAX_POSITION;
            topLimitTripped = true;
            halt();
        }
    }

    /**
     * Stops the motion engine and clears its active motion.
     */
    private void halt() {
        speed = 0.0;
        state = MotionState.STOPPED;
    }

    /**
     * Constrains the supplied numeric value to the permitted range.
     *
     * @param p value for p
     * @return the resulting measurement or calculated value
     */
    private static double clamp(double p) {
        return Math.max(MIN_POSITION, Math.min(MAX_POSITION, p));
    }

    // extra values for the gui
    /** One of STOPPED, ACCELERATING, CRUISING, DECELERATING. For the motionState label. */
    @Override
    public synchronized MotionState getState() {
        return state;
    }

    /** Floor whose sensor zone contains the car, or 0 between zones. For the currentFloor label. */
    public synchronized int getCurrentFloor() {
        return FloorSensors.currentFloor(position);
    }

    /**
     * Returns an immutable snapshot of the cabin’s current state. The method is synchronized on this instance.
     *
     * @return an immutable snapshot of the cabin state
     */
    @Override
    public synchronized CabinSnapshot snapshot() {
        return new CabinSnapshot(position, speed, direction, state,
                FloorSensors.currentFloor(position), topLimitTripped, bottomLimitTripped);
    }
}