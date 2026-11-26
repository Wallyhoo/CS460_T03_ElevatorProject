package T04ElevatorMotion.src.harness;

import Utilities.Message;
import bus.BusCodes;
import bus.BusMessageDispatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Plays the command center: publishes one cabin call at a time and waits for the car to arrive. */
public class CmdCtrStandIn {

    private final BusMessageDispatcher center;
    private final int elevator;
    private final int[] floors;
    private final int pauseTicks;
    private final List<Integer> arrivals = new ArrayList<>();

    private int next = 0;
    private int waitingFor = 0;      //0 means no call is outstanding
    private int pauseLeft = 0;
    private boolean lastStopped = false;   //newest CABIN_STATUS: was the car stopped
    private int lastFloor = -1;            //newest CABIN_STATUS: floor, -1 means nothing heard yet

    /**
     * Creates and initializes the CmdCtrStandIn instance.
     *
     * @param center message dispatcher used to communicate with the command center
     * @param elevator elevator identifier
     * @param floors number of floors in the shaft
     * @param pauseTicks number of simulation ticks to pause between requests
     */
    public CmdCtrStandIn(BusMessageDispatcher center, int elevator, int[] floors, int pauseTicks) {
        this.center = center;
        this.elevator = elevator;
        this.floors = floors;
        this.pauseTicks = pauseTicks;
        center.subscribe(BusCodes.CABIN_STATUS, elevator);
    }

    /** Call once per simulation step from the beforeStep hook. */
    public void update() {
        readStatus();

        if (waitingFor != 0) {
            //Arrived means: the newest CABIN_STATUS says stopped at the target floor.
            if (lastStopped && lastFloor == waitingFor) {
                arrivals.add(waitingFor);
                waitingFor = 0;
                pauseLeft = pauseTicks;
            }
            return;
        }
        if (pauseLeft > 0) {
            pauseLeft -= 1;
            return;
        }
        if (next < floors.length) {
            waitingFor = floors[next];
            next += 1;
            center.publish(Message.ofPayload(BusCodes.CABIN_CALL, elevator, waitingFor));
        }
    }

    /**
     * Reports whether the harness or scenario has completed.
     *
     * @return whether the requested condition is true
     */
    public boolean isFinished() {
        return next >= floors.length && waitingFor == 0;
    }

    /**
     * Returns the floor numbers recorded as arrivals, in arrival order.
     *
     * @return the resulting List value
     */
    public List<Integer> getArrivals() {
        return arrivals;
    }

    /** Reads everything queued for CABIN_STATUS and keeps the newest one. */
    private void readStatus() {
        Optional<Message> m = center.get(BusCodes.CABIN_STATUS, elevator);
        while (m.isPresent()) {
            lastStopped = m.get().flag(1);
            lastFloor = m.get().payload(2);
            m = center.get(BusCodes.CABIN_STATUS, elevator);
        }
    }
}