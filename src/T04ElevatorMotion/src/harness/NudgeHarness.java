package T04ElevatorMotion.src.harness;

import bus.BusCodes;
import bus.BusMessageDispatcher;
import harness.CmdCtrStandIn;
import manager.TripController;
import sim.MotionEngine;

import java.util.List;

/**
 * Scenario: the car brakes late, so it passes the sensors of the target floor and stops past
 * them. The trip controller then nudges it back until it is level. The GUI shows the in-between
 * sensor color while the nudging happens, then lime green once the car is level.
 */
public class NudgeHarness {

    /** How much later than normal the car brakes. It ends up about 0.8 ft past the floor. */
    public static final double BRAKE_DELAY_FEET = Math.min(0.6, 0.25 * MotionEngine.brakingDistance(MotionEngine.CRUISE_SPEED));

    private static final int PAUSE_TICKS = (int) Math.ceil(2.0 / MotionEngine.TIME_STEP);   // 2 seconds

    /** Skips floors 1 and 10 so the overshoot stays inside the 1 ft lever margin. */
    public static final int[] FLOORS = {4, 8, 3, 6};

    private final CmdCtrStandIn runner;

    /**
     * Creates and initializes the NudgeHarness instance.
     *
     * @param commandCenter message dispatcher used to communicate with the command center
     * @param trip trip controller for the cabin
     */
    public NudgeHarness(BusMessageDispatcher commandCenter, TripController trip) {
        trip.setBrakeDelay(BRAKE_DELAY_FEET);
        runner = new CmdCtrStandIn(commandCenter, BusCodes.ELEVATOR_1, FLOORS, PAUSE_TICKS);
    }

    /** Call once per simulation step, from the beforeStep hook. */
    public void update() {
        runner.update();
    }

    /**
     * Reports whether the harness or scenario has completed.
     *
     * @return whether the requested condition is true
     */
    public boolean isFinished() {
        return runner.isFinished();
    }

    /**
     * Returns the floor numbers recorded as arrivals, in arrival order.
     *
     * @return the resulting List value
     */
    public List<Integer> getArrivals() {
        return runner.getArrivals();
    }
}