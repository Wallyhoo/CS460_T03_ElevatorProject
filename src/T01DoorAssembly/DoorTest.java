package T01DoorAssembly;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Tests with no GUI. Run with: make test
 *
 *   Part 0  DoorTwin's sensors (the simulation is stepped by hand, so no waiting).
 *   Part 1  DoorManager's rules, using a fake door we control by hand (no bus, no sockets).
 *   Part 2  The same messages as 8-byte bus messages, still in memory.
 *   Part 3  The whole chain over the real T03 Software Bus (skipped if no bus on localhost:6000).
 */
public class DoorTest {

    /** A door we control by hand. The manager only sees it through DoorApi. */
    static class FakeDoor implements DoorApi {
        volatile boolean fullyOpen, fullyClosed = true, blocked;
        volatile int openCalls, closeCalls;
        public void open()  { openCalls++;  fullyClosed = false; }
        public void close() { closeCalls++; fullyOpen = false; }
        public boolean isFullyOpen()   { return fullyOpen; }
        public boolean isFullyClosed() { return fullyClosed; }
        public boolean hasObstruction() { return blocked; }
        void finishOpening() { fullyOpen = true; }
        void finishClosing() { fullyClosed = true; }
    }

    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name);
        if (!ok) failures++;
    }

    static boolean waitFor(BooleanSupplier cond, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) sleep(20);
        return cond.getAsBoolean();
    }

    static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    public static void main(String[] args) throws Exception {
        sensors();
        rules();
        messages();
        overTheBus();
        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    //part 0: sensors

    static void sensors() {
        System.out.println("Part 0: door sensors");
        DoorTwin t = new DoorTwin(1);
        check("starts closed: middle sensor on, end sensors off",
                t.isClosedSensor() && !t.isOpenLeftSensor() && !t.isOpenRightSensor() && t.isFullyClosed());

        t.open();
        t.update(2.0);                                      // part way (the door takes 5 seconds)
        check("part way open: no end sensor and not the middle one",
                !t.isClosedSensor() && !t.isOpenLeftSensor() && !t.isOpenRightSensor());
        check("part way open: neither fully open nor fully closed", !t.isFullyOpen() && !t.isFullyClosed());

        t.update(4.0);                                      // past the end
        check("fully open: both end sensors on, middle off",
                t.isOpenLeftSensor() && t.isOpenRightSensor() && !t.isClosedSensor() && t.isFullyOpen());

        t.close();
        check("closing: the end sensors let go at once", !t.isOpenLeftSensor() && !t.isOpenRightSensor() && !t.isFullyOpen());
        t.update(6.0);
        check("fully closed again: middle sensor on", t.isClosedSensor() && t.isFullyClosed());

        check("nothing in the way: no obstruction", !t.hasObstruction());
        t.placeObjectInDoorway(true);
        check("object in the doorway: the beam sensor detects it", t.isBeamBroken() && !t.isSillLoaded() && t.hasObstruction());
        t.placeObjectInDoorway(false);
        t.placeObjectOnSill(true);
        check("object on the sill: the sill sensor detects it", t.isSillLoaded() && !t.isBeamBroken() && t.hasObstruction());
        t.placeObjectOnSill(false);
        check("object removed: clear again", !t.hasObstruction());
        t.stop();
    }

    //part 1: rules

    static void rules() {
        System.out.println("Part 1: door rules");
        final FakeDoor first = new FakeDoor();
        FakeDoor door = first;
        DoorManager m = new DoorManager(door, 1, 0);        // 0 = auto-close off
        m.start();

        m.requestClose();
        check("close when already closed -> nothing happens", door.closeCalls == 0);
        m.requestOpen();
        check("open -> door.open() called", door.openCalls == 1);
        check("state is OPENING", m.getState() == DoorManager.State.OPENING);
        m.requestOpen();
        check("open again while opening -> nothing happens", door.openCalls == 1);

        door.finishOpening();
        check("state is OPEN", m.getState() == DoorManager.State.OPEN);
        m.requestOpen();
        check("open when already open -> nothing happens", door.openCalls == 1);

        door.blocked = true;
        m.requestClose();
        check("close while blocked -> ignored", door.closeCalls == 0);
        door.blocked = false;

        m.requestClose();
        check("close when clear -> door.close() called", door.closeCalls == 1);
        check("state is CLOSING", m.getState() == DoorManager.State.CLOSING);
        m.requestClose();
        check("close again while closing -> nothing happens", door.closeCalls == 1);

        int before = door.openCalls;
        door.blocked = true;
        check("blocked while closing -> manager reopens the door", waitFor(() -> first.openCalls > before, 1000));
        door.blocked = false;
        door.finishOpening();

        m.requestClose();
        int opensBefore = door.openCalls;
        m.requestOpen();
        check("open while closing -> reverses",
                door.openCalls == opensBefore + 1 && m.getState() == DoorManager.State.OPENING);
        m.stop();

        // auto-close
        door = new FakeDoor();
        m = new DoorManager(door, 1, 0.3);
        m.start();
        m.requestOpen();
        door.finishOpening();
        final FakeDoor d1 = door;
        check("auto-close after the hold time", waitFor(() -> d1.closeCalls == 1, 1500));
        m.stop();

        door = new FakeDoor();
        m = new DoorManager(door, 1, 0.3);
        m.start();
        m.requestOpen();
        door.finishOpening();
        door.blocked = true;
        sleep(800);
        final FakeDoor d2 = door;
        check("no auto-close while blocked", d2.closeCalls == 0);
        door.blocked = false;
        check("auto-close once clear", waitFor(() -> d2.closeCalls == 1, 1500));
        m.stop();
    }

    // part 2: bus messages

    static void messages() {
        System.out.println("Part 2: 8-byte bus messages (in memory)");
        FakeDoor door = new FakeDoor();
        DoorManager m = new DoorManager(door, 2, 0);         // this is car 2
        List<byte[]> sent = new ArrayList<>();
        m.attach(msg -> { synchronized (sent) { sent.add(msg); } });
        m.start();

        m.onMessage(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, 1, 1));
        check("command for another car is ignored", door.openCalls == 0);
        m.onMessage(DoorBus.encode(DoorBus.TOPIC_DOOR_STATE, 2, 1));
        check("other topics are ignored", door.openCalls == 0);
        m.onMessage(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, 2, 1));
        check("open command for this car -> door opens", door.openCalls == 1);

        door.finishOpening();
        check("DOOR_STATE=2 (open) is published for car 2", waitFor(() -> lastValue(sent, DoorBus.TOPIC_DOOR_STATE, 2) == 2, 1500));
        door.blocked = true;
        check("DOOR_OBSTRUCTION=1 is published", waitFor(() -> lastValue(sent, DoorBus.TOPIC_DOOR_OBSTRUCTION, 2) == 1, 1500));
        door.blocked = false;

        m.onMessage(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, 0, 0));
        check("subtopic 0 (all cars) is accepted: close", door.closeCalls == 1);
        m.stop();
    }

    static long lastValue(List<byte[]> sent, int topic, int car) {
        synchronized (sent) {
            for (int i = sent.size() - 1; i >= 0; i--) {
                byte[] x = sent.get(i);
                if (DoorBus.topic(x) == topic && DoorBus.subtopic(x) == car) return DoorBus.value(x);
            }
        }
        return -1;
    }

    //part 3: real Software Bus

    static void overTheBus() throws Exception {
        System.out.println("Part 3: over the real Software Bus (localhost:6000)");
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress("localhost", 6000), 1000);
        } catch (IOException e) {
            System.out.println("  SKIPPED  no bus running. Start it with: SBMain 6000 (in the T03 repo)");
            return;
        }
        DoorTwin twin = new DoorTwin(1);
        DoorManager m = new DoorManager(twin, 1, 0);
        m.start();
        DoorDash dash = new DoorDash(m, "localhost", 6000);
        dash.start();

        OutputStream out = s.getOutputStream();
        DataInputStream in = new DataInputStream(s.getInputStream());
        out.write(DoorBus.encode(DoorBus.SUBSCRIBE, DoorBus.TOPIC_DOOR_STATE, 1));          // subscribe to door state
        int[] state = {-1};
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    out.write(DoorBus.encode(DoorBus.GET, DoorBus.TOPIC_DOOR_STATE, 1));   // ask for news
                    byte[] msg = new byte[DoorBus.SIZE];
                    in.readFully(msg);
                    if (msg[0] != DoorBus.NO_MESSAGE) state[0] = (int) DoorBus.value(msg);
                    Thread.sleep(20);
                }
            } catch (Exception ignored) { }
        });
        t.setDaemon(true);
        t.start();

        check("status arrives over the bus (CLOSED)", waitFor(() -> state[0] == 0, 3000));
        out.write(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, 1, 1));            // open
        check("OPEN command over the bus -> door OPEN", waitFor(() -> state[0] == 2, 12000));
        out.write(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, 1, 0));            // close
        check("CLOSE command over the bus -> door CLOSED", waitFor(() -> state[0] == 0, 12000));

        s.close();
        dash.stop();
        m.stop();
        twin.stop();
    }
}
