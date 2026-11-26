package T04ElevatorMotion.src.harness;

import manager.FloorSequencer;
import manager.TripController;
import sim.MotionEngine;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Test harness standing in for the elevator manager.
 * Owns the clock and the test route; trip logic lives in TripController
 * and the floor cycle in FloorSequencer, so removing this class loses nothing vital.
 */
public class TestHarness {

    private static final int[] ROUTE = {2, 10, 4, 1};
    private static final double PAUSE_SECONDS = 2.0;
    private static final long STEP_MICROS = Math.round(MotionEngine.TIME_STEP * 1_000_000);   // one simulation step in real time
    private final TripController controller;
    private final FloorSequencer sequencer;
    private final Runnable updateTimeStep;
    //Volatile is called from the main thread and the field is read by the harness's clock thread
    private volatile Runnable beforeStep = () -> { };

    private final ScheduledExecutorService clock =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "test-harness-clock");
                thread.setDaemon(true);
                return thread;
            });

    /**
     * Creates and initializes the TestHarness instance.
     *
     * @param controller trip controller used by the harness
     * @param updateTimeStep callback that advances the simulation by one time step
     */
    public TestHarness(TripController controller, Runnable updateTimeStep) {
        this.controller = controller;
        this.sequencer = new FloorSequencer(controller, ROUTE, PAUSE_SECONDS);
        this.updateTimeStep = updateTimeStep;
    }

    /**
     * Starts the component or begins its scheduled work.
     */
    public void start() {
        clock.scheduleAtFixedRate(this::onUpdate, 0, STEP_MICROS, TimeUnit.MICROSECONDS);
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
     * Runs the configured update callback for the current harness step.
     */
    void onUpdate() {
        try {
            beforeStep.run();
            sequencer.update(MotionEngine.TIME_STEP);
            controller.controlStep();
            updateTimeStep.run();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }
}
