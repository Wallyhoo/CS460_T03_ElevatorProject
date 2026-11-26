package T04ElevatorMotion.src.manager;

import T04ElevatorMotion.src.sim.MotionEngine;

/**
 * Coordinates simulation ticks, callbacks, and adjustable simulation time scale.
 */
public class SimulationManager {

    private final TripController tripController;
    private final Runnable tick;
    private long lastFramesStartTime = -1;
    private double accumulator = 0.0;
    private volatile double timeScale = 1.0;   //set from the GUI thread, read by the elevator's own thread
    private static final double longestCatchUp = 0.25;
    private Runnable beforeStep = () -> { };

    /**
     * Creates and initializes the SimulationManager instance.
     *
     * @param tripController value for tripController
     * @param tick simulation tick callback
     */
    public SimulationManager(TripController tripController, Runnable tick) {
        this.tripController = tripController;
        this.tick = tick;
    }

    /**
     * Sets the callback that runs immediately before each simulation step.
     *
     * @param beforeStep callback to run before each simulation step
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    public void setBeforeStep(Runnable beforeStep) {
        if (beforeStep == null) {
            throw new IllegalArgumentException("beforeStep must not be null");
        }
        this.beforeStep = beforeStep;
    }

    /**
     * Updates the component for the current simulation step.
     *
     * @param currentNanos current time in nanoseconds
     */
    public void update(long currentNanos) {
        if (lastFramesStartTime == -1) {
            lastFramesStartTime = currentNanos;
            return;
        }
        double frameSeconds = (currentNanos - lastFramesStartTime)/1000000000.0;
        lastFramesStartTime = currentNanos;
        accumulator += frameSeconds * timeScale;
        if (accumulator > longestCatchUp) {
            accumulator = longestCatchUp;
        }
        while (accumulator >= MotionEngine.TIME_STEP) {
            beforeStep.run();
            tripController.controlStep();
            tick.run();
            accumulator -= MotionEngine.TIME_STEP;
        }
    }

    /**
     * Sets the multiplier applied to simulation time.
     *
     * @param timeScale value for timeScale
     */
    public void setTimeScale(double timeScale) {
        this.timeScale = timeScale;
    }

    /**
     * Requests travel to the specified floor.
     *
     * @param floor floor number
     */
    public void goToFloor(int floor) {
        tripController.goToFloor(floor);
    }

    /**
     * Reports whether a trip is currently active.
     *
     * @return whether the requested condition is true
     */
    public boolean isTripActive() {
        return tripController.isTripActive();
    }
}
