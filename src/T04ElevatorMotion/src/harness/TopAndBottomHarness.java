package T04ElevatorMotion.src.harness;

import bus.BusCodes;
import bus.BusMessageDispatcher;

import java.util.List;

/** Scenario: the car goes straight to the top floor, then straight to floor 1. */
public class TopAndBottomHarness {

    private static final int PAUSE_TICKS = 40;   // 2 seconds at 0.05 s per step

    public static final int[] FLOORS = {10, 1};

    private final CmdCtrStandIn runner;

    /**
     * Creates and initializes the TopAndBottomHarness instance.
     *
     * @param commandCenter message dispatcher used to communicate with the command center
     */
    public TopAndBottomHarness(BusMessageDispatcher commandCenter) {
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