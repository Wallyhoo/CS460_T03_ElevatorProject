package T04ElevatorMotion.src.bus;

import T04ElevatorMotion.src.api.MotionControllerAPI;
import T04ElevatorMotion.src.bus.BusMessageDispatcher;
import T04ElevatorMotion.src.manager.TripController;
import T04ElevatorMotion.src.sim.MotionEngine;
import Utilities.Message;
import java.util.ArrayDeque;
import java.util.Optional;

/**
 * Translates between elevator cabin state and software-bus messages.
 */
public class BusCabinGateway {

    //The topics that ask this cabin to go to a floor. All three carry the floor in payload byte 1.
    //We have no doors, so GOTO (keep doors closed) and SERVICE (open doors) are treated like CABIN_CALL.
    private static final int[] CALL_TOPICS = {BusCodes.CABIN_CALL, BusCodes.GOTO, BusCodes.SERVICE};

    private final MotionControllerAPI motion;
    private final TripController trip;
    private final BusMessageDispatcher bus;
    private final int elevator;
    private final ArrayDeque<Integer> pendingFloors = new ArrayDeque<>();
    private int lastFault = 0;          //0 = nothing announced
    private int lastAlignment = -1;     //-1 = nothing announced yet
    private boolean statusAnnounced = false;


    /**
     * Creates and initializes the BusCabinGateway instance.
     *
     * @param motion value for motion
     * @param trip trip controller for the cabin
     * @param bus software-bus instance
     * @param elevator elevator identifier
     * @throws IllegalArgumentException if an argument or state violates the method’s requirements
     */
    public BusCabinGateway(MotionControllerAPI motion, TripController trip,
                           BusMessageDispatcher bus, int elevator) {

        if (elevator < BusCodes.ELEVATOR_1 || elevator > BusCodes.ELEVATOR_4) {
            throw new IllegalArgumentException("elevator must be 1 to 4: " + elevator);
        }
        this.motion = motion;
        this.trip = trip;
        this.bus = bus;
        this.elevator = elevator;

        bus.register();
        for (int topic : CALL_TOPICS) {
            bus.subscribe(topic, elevator);
        }
    }

    /** Empty the bus into the waiting queue */
    private void readCalls() {
        boolean lockedOut = currentFault() != 0;
        if (lockedOut) {
            pendingFloors.clear();       //a tripped car cannot serve calls
        }

        //Calls from different topics are read one topic after the other, not in the order they were sent
        for (int topic : CALL_TOPICS) {
            Optional<Message> call = bus.get(topic, elevator);
            //Removes the oldest message each time until empty
            while (call.isPresent()) {
                int floor = call.get().payload(1);
                //skips a floor that is already waiting so a repeated button press doesn't queue twice
                if (!lockedOut && floor >= 1 && floor <= MotionEngine.FLOORS && !pendingFloors.contains(floor)) {
                    pendingFloors.addLast(floor);
                }
                call = bus.get(topic, elevator);
            }
        }
    }

    /** Take the next waiting floor when the cabin is free */
    private void startNextCall() {
        //Only starts a trip if the cabin is idle
        if (pendingFloors.isEmpty() || trip.isTripActive() || motion.isMoving()) {
            return;
        }
        trip.goToFloor(pendingFloors.pollFirst());
    }

    /** Send on the bus only what has changed */
    private void publishChanges() {
        int fault = currentFault();
        if (fault != lastFault) {
            bus.publish(Message.ofPayload(BusCodes.CAR_FAULT, elevator, fault));
            lastFault = fault;
        }

        //ALIGN: the sensors at the ends of the shaft. Bottom is the floor 1 zone and top is the floor 10 zone.
        int alignment = BusCodes.ALIGN_NONE;
        if (motion.isBottomAligned()) {
            alignment += BusCodes.ALIGN_BOTTOM;
        }
        if (motion.isTopAligned()) {
            alignment += BusCodes.ALIGN_TOP;
        }
        if (alignment != lastAlignment) {
            bus.publish(Message.ofPayload(BusCodes.ALIGN, elevator, alignment));
            lastAlignment = alignment;
        }

        //CABIN_STATUS is only published when the car has stopped, so nothing is sent while it travels or levels.
        if (motion.isMoving() || trip.isTripActive()) {
            statusAnnounced = false;
            return;
        }
        if (statusAnnounced) {
            return;
        }
        int floor = 0;
        for (int f = 1; f <= MotionEngine.FLOORS; f++) {
            if (motion.isFloorAligned(f)) {
                floor = f;
            }
        }
        //A car stopped between floors (a tripped safety lever) has no floor to report
        if (floor != 0) {
            bus.publish(Message.ofPayload(BusCodes.CABIN_STATUS, elevator, 1, floor, BusCodes.DIR_IDLE));
            statusAnnounced = true;
        }
    }

    /** Updates the component for the current simulation step.*/
    public void update() {
        readCalls();
        publishChanges(); //report the car's state first so a finished trip shows as stopped
        startNextCall(); //only then start the next queued call

    }

    /**
     * Returns the current elevator fault code, or zero when no fault is active.
     *
     * @return the resulting integer value
     */
    private int currentFault() {
        if (motion.isTopLimitTripped()) {
            return BusCodes.FAULT_TOP_LIMIT;
        }
        if (motion.isBottomLimitTripped()) {
            return BusCodes.FAULT_BOTTOM_LIMIT;
        }
        return 0;
    }
}