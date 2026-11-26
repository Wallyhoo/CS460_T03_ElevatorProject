package T04ElevatorMotion.src.harness;

import Utilities.Message;
import api.Direction;
import api.MotionControllerAPI;
import bus.BusCodes;
import bus.BusMessageDispatcher;

import java.util.Optional;

/**
 * Scenario: after a short delay the car runs away (motor started with no trip, so nothing brakes)
 * until it hits a safety lever. The harness waits for the fault message on the bus.
 */
public class LeverStopHarness {

    private final MotionControllerAPI car;
    private final BusMessageDispatcher listener;
    private final int elevator;
    private final Direction runaway;
    private final int delayTicks;

    private int ticks = 0;
    private boolean started = false;
    private long fault = 0;

    /**
     * Creates and initializes the LeverStopHarness instance.
     *
     * @param car cabin assembly being checked
     * @param listener value for listener
     * @param elevator elevator identifier
     * @param runaway direction of runaway travel
     * @param delayTicks value for delayTicks
     */
    public LeverStopHarness(MotionControllerAPI car, BusMessageDispatcher listener, int elevator,
                            Direction runaway, int delayTicks) {
        this.car = car;
        this.listener = listener;
        this.elevator = elevator;
        this.runaway = runaway;
        this.delayTicks = delayTicks;
        listener.subscribe(BusCodes.CAR_FAULT, elevator);
    }

    /** Call once per simulation step from the beforeStep hook. */
    public void update() {
        ticks += 1;
        if (started == false && ticks >= delayTicks) {
            started = true;
            System.out.println("Scenario started: runaway cabin " + runaway + ", " +
                    " waiting for lever to trip to signal auto braking the cabin.");
            car.startMotor(runaway);
        }
        Optional<Message> message = listener.get(BusCodes.CAR_FAULT, elevator);
        while (message.isPresent()) {
            fault = message.get().payload(1);
            System.out.println("EMERGENCY on the bus: " + describe(fault));
            message = listener.get(BusCodes.CAR_FAULT, elevator);
        }
    }

    /**
     * Reports whether the harness or scenario has completed.
     *
     * @return whether the requested condition is true
     */
    public boolean isFinished() {
        return fault != 0;
    }

    /**
     * Returns the fault code recorded by the harness.
     *
     * @return the resulting numeric value
     */
    public long getFault() {
        return fault;
    }

    /**
     * Returns a readable description of the supplied value.
     *
     * @param code fault or status code
     * @return the resulting text
     */
    private static String describe(long code) {
        if (code == BusCodes.FAULT_TOP_LIMIT) {
            return "car hit the lever above floor 10";
        }
        if (code == BusCodes.FAULT_BOTTOM_LIMIT) {
            return "car hit the lever below floor 1";
        }
        return "unknown fault " + code;
    }
}