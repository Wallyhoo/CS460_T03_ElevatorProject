package T01DoorAssembly;

/**
 * The brain of the door. It holds all the door rules and controls the door only
 * through DoorApi, so it works the same with the simulated DoorTwin or with real
 * hardware. It has no networking: requests arrive through onMessage(), and the
 * door's status leaves through a DoorBus.Link supplied by the bus layer.
 *
 * Rules:
 *   1. Blocked while closing           -> reopen.
 *   2. Close requested while blocked   -> ignore it.
 *   3. Open requested while closing    -> reverse and open.
 *   4. Open while already open (or opening), close while already closed
 *      (or closing)                    -> do nothing.
 *   5. Open for holdSec seconds with nothing in the way -> close by itself.
 *      (The wait restarts while the doorway is blocked. holdSec = 0 turns this off.)
 *
 * The manager only knows what DoorApi tells it (fully open, fully closed, blocked),
 * so it remembers which way it last sent the door to tell "opening" from "closing".
 */
public class DoorManager {

    /** What the door is doing. The order matches the values sent in DOOR_STATE (see DoorBus). */
    public enum State { CLOSED, OPENING, OPEN, CLOSING }

    public static final double DEFAULT_HOLD_SEC = 10.0;

    private static final long TICK_MS = 50;       // how often the sensors are checked
    private static final long RESEND_STATUS_MS = 500; // status is re-sent this often (the bus keeps no history)

    private final DoorApi door;
    private final int carId;
    private final long holdMs;

    private boolean lastWasOpen = false;  // which way the door was last sent
    private long holdStartMs = 0;         // when the door became fully open (0 = not open)

    private volatile DoorBus.Link link;
    private int lastState = -1;
    private int lastBlocked = -1;
    private long lastSendMs = 0;

    private volatile boolean running = false;

    public DoorManager(DoorApi door, int carId) {
        this(door, carId, DEFAULT_HOLD_SEC);
    }

    public DoorManager(DoorApi door, int carId, double holdSec) {
        this.door = door;
        this.carId = carId;
        this.holdMs = Math.round(holdSec * 1000);
    }

    public int getCarId() {
        return carId;
    }

    /** Starts the loop that watches the sensors and applies rules 1 and 5. */
    public synchronized void start() {
        if (running) return;
        running = true;
        Thread loop = new Thread(() -> {
            while (running) {
                tick();
                try {
                    Thread.sleep(TICK_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "DoorManager-" + carId);
        loop.setDaemon(true);
        loop.start();
    }

    public void stop() {
        running = false;
    }

    /** Connects the manager's status messages to the bus layer. */
    public synchronized void attach(DoorBus.Link link) {
        this.link = link;
        lastState = -1; // forces a fresh status message on the next tick
        lastBlocked = -1;
    }

    public void detach() {
        this.link = null;
    }

    /**
     * Entry point for the bus layer: call this with every 8-byte message delivered
     * for DOOR_COMMAND (topic 8). Body 1 = open, 0 = close. Messages for another
     * car are ignored; subtopic 0 means all cars.
     */
    public void onMessage(byte[] message) {
        int sub = DoorBus.subtopic(message);
        if (sub != carId && sub != 0) return;
        if (DoorBus.topic(message) != DoorBus.TOPIC_DOOR_COMMAND) return;
        long v = DoorBus.value(message);
        if (v == 1) requestOpen();
        else if (v == 0) requestClose();
    }

    /** Someone wants the door open (rules 3 and 4). */
    public synchronized void requestOpen() {
        State s = state();
        if (s == State.OPEN || s == State.OPENING) return;
        door.open();
        lastWasOpen = true;
        holdStartMs = 0;
    }

    /** Someone wants the door closed (rules 2 and 4). */
    public synchronized void requestClose() {
        State s = state();
        if (s == State.CLOSED || s == State.CLOSING) return;
        if (door.hasObstruction()) return;
        door.close();
        lastWasOpen = false;
        holdStartMs = 0;
    }

    public synchronized State getState() {
        return state();
    }

    /** What the door is doing, worked out from DoorApi and the last command. */
    private State state() {
        if (door.isFullyOpen()) return State.OPEN;
        if (door.isFullyClosed()) return State.CLOSED;
        return lastWasOpen ? State.OPENING : State.CLOSING;
    }

    /** One look at the sensors: applies rules 1 and 5, then sends the status if due. */
    private void tick() {
        int st;
        int blocked;
        boolean send;
        synchronized (this) {
            long now = System.currentTimeMillis();
            State s = state();
            boolean isBlocked = door.hasObstruction();

            if (s == State.CLOSING && isBlocked) { // rule 1
                door.open();
                lastWasOpen = true;
                s = State.OPENING;
            }

            if (s == State.OPEN) { // rule 5
                if (holdStartMs == 0 || isBlocked) holdStartMs = now;
                if (holdMs > 0 && !isBlocked && now - holdStartMs >= holdMs) {
                    door.close();
                    lastWasOpen = false;
                    s = State.CLOSING;
                    holdStartMs = 0;
                }
            } else {
                holdStartMs = 0;
            }

            st = s.ordinal();
            blocked = isBlocked ? 1 : 0;
            boolean changed = st != lastState || blocked != lastBlocked;
            send = link != null && (changed || now - lastSendMs >= RESEND_STATUS_MS);
            if (send) {
                lastState = st;
                lastBlocked = blocked;
                lastSendMs = now;
            }
        }
        if (send) { // outside the lock so the bus layer can never deadlock us
            DoorBus.Link l = link;
            if (l != null) {
                l.send(DoorBus.encode(DoorBus.TOPIC_DOOR_STATE, carId, st));
                l.send(DoorBus.encode(DoorBus.TOPIC_DOOR_OBSTRUCTION, carId, blocked));
            }
        }
    }
}
