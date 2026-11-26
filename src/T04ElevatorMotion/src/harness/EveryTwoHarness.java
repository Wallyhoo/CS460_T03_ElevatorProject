package T04ElevatorMotion.src.harness;

import bus.BusCodes;
import bus.BusMessageDispatcher;

import java.util.List;

/**
 * Car goes up every two floors, stopping at each one.
 */

public class EveryTwoHarness {
    private static final int PAUSE_TICKS = 40;   // 2 seconds at 0.05 s per step

    public static final int[] FLOORS = {3, 5, 7, 9};

    private final CmdCtrStandIn runner;

    /**
     * Creates and initializes the EveryTwoHarness instance.
     *
     * @param commandCenter message dispatcher used to communicate with the command center
     */
    public EveryTwoHarness(BusMessageDispatcher commandCenter) {
        runner = new CmdCtrStandIn(commandCenter, BusCodes.ELEVATOR_1, FLOORS, PAUSE_TICKS);
    }

    /**
     * Updates the component for the current simulation step.
     */
    public void update() {
        runner.update();
    }

    /** True if the car has arrived at every floor
     *
     * @return whether the requested condition is true
     */
    public boolean isFinished() {
        return runner.isFinished();
    }

    /** Floors the car has visited in order
     *
     * @return the resulting List value
     */
    public List<Integer> getArrivals() {
        return runner.getArrivals();
    }
}
