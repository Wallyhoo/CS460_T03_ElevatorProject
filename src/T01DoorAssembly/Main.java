package T01DoorAssembly;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * Test harness. Runs the door side (DoorTwin + DoorManager + DoorDash) and walks through the
 * tests, with everything going through the T03 Software Bus:
 *
 *   1. Obstruction: the door reports it, it is published on the bus, read back, and printed.
 *   2. Close: a command is published on the bus, the door side reads it and closes the door.
 *   3. Open
 *   4. Close is ignored while the doorway is blocked
 *   5. Blocked while closing: the door reopens
 *   6. Open while closing: the door reverses
 *   7. Auto-close after the door has been open for a while
 *
 *   java Main [host] [port]            Runs its own door (no window) plus the tests.
 *   java Main --client [host] [port]   Tests only. Uses the door in the GUI, so start the Software
 *                                      Bus first, then the GUI (make sim), then this. The sensor
 *                                      steps ask you to click the GUI's Obstruction Sensor button.
 *
 * Defaults: localhost 6000. The Software Bus must be running.
 */
public class Main {

    static final int CAR = 1;
    static final double HOLD_SEC = 8.0;   // door stays open this long before closing itself
    static int failed = 0;
    static final long PAUSE_MS = 2500;    // pause before each test so the demo is easy to follow
    static final long HALF_WAY_MS = 2500; // about half of the 5 seconds the door takes to close
    static final long STEP_MS = 1000;     // pause between the numbered steps of tests 1 and 2
    static final String[] STATE = {"CLOSED", "OPENING", "OPEN", "CLOSING"};

    static volatile int state = -1;       // last DOOR_STATE read from the bus
    static volatile int blocked = -1;     // last DOOR_OBSTRUCTION read from the bus
    static volatile byte[] obstructionMsg; // last DOOR_OBSTRUCTION message read from the bus
    static OutputStream out;
    static boolean client = false;        // --client: the door lives in the GUI, not in this program
    static DoorTwin twin;                 // our own door (not used with --client)

    public static void main(String[] args) throws Exception {
        java.util.List<String> rest = new java.util.ArrayList<>();
        for (String a : args) {
            if (a.equals("--client")) client = true; else rest.add(a);
        }
        String host = rest.size() > 0 ? rest.get(0) : "localhost";
        int port = rest.size() > 1 ? Integer.parseInt(rest.get(1)) : 6000;

        // door side: the door (simulated), the brain, and the bus connection
        DoorApi door = null;
        DoorManager manager = null;
        DoorDash dash = null;
        if (client) {
            System.out.println("Client mode: using the door in the GUI.");
        } else {
            twin = new DoorTwin(CAR);
            door = twin;
            manager = new DoorManager(door, CAR, HOLD_SEC);
            manager.start();
            dash = new DoorDash(manager, host, port);
            dash.start();
        }

        // test side: its own connection to the bus
        Socket socket = new Socket(host, port);
        out = socket.getOutputStream();
        DataInputStream in = new DataInputStream(socket.getInputStream());
        send(DoorBus.encode(DoorBus.SUBSCRIBE, DoorBus.TOPIC_DOOR_STATE, CAR));          // subscribe
        send(DoorBus.encode(DoorBus.SUBSCRIBE, DoorBus.TOPIC_DOOR_OBSTRUCTION, CAR));
        Thread reader = new Thread(() -> {
            try {
                while (true) {
                    for (int topic : new int[]{DoorBus.TOPIC_DOOR_STATE, DoorBus.TOPIC_DOOR_OBSTRUCTION}) {
                        send(DoorBus.encode(DoorBus.GET, topic, CAR));              // ask the bus for news
                        byte[] msg = new byte[DoorBus.SIZE];
                        in.readFully(msg);
                        if (msg[0] == DoorBus.NO_MESSAGE) continue;                        // nothing new
                        int v = (int) DoorBus.value(msg);
                        if (topic == DoorBus.TOPIC_DOOR_STATE) state = v;
                        else { blocked = v; obstructionMsg = msg; }
                    }
                    Thread.sleep(20);
                }
            } catch (Exception e) {
                // socket closed: we are done
            }
        });
        reader.setDaemon(true);
        reader.start();

        // get the door open first so there is something to close
        if (!waitFor(() -> state >= 0)) {
            System.out.println("No status from the door side - is the bus running"
                    + (client ? " and the GUI started?" : "?"));
            System.exit(1);
        }
        System.out.println("Setup: opening the door");
        send(DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, CAR, 1));
        waitFor(() -> state == 2);
        System.out.println("   door is OPEN\n");

        //test 1: obstruction
        pause(); System.out.println("TEST 1: obstruction");
        sensor(true);
        System.out.println(client ? "   1. sensor tripped in the GUI"
                                  : "   1. door side: hasObstruction() = " + door.hasObstruction());
        Thread.sleep(STEP_MS);
        System.out.println("   2. door side published DOOR_OBSTRUCTION on the bus");
        Thread.sleep(STEP_MS);
        waitFor(() -> blocked == 1);
        System.out.println("   3. read from the bus: " + Arrays.toString(obstructionMsg) + "  (DOOR_OBSTRUCTION = " + blocked + ")");
        Thread.sleep(STEP_MS);
        System.out.println("   4. terminal: OBSTRUCTION DETECTED\n");
        Thread.sleep(STEP_MS);
        sensor(false);
        waitFor(() -> blocked == 0);

        //test 2: close the door
        pause(); System.out.println("TEST 2: close the door");
        byte[] close = DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, CAR, 0);
        System.out.println("   1. published the command on the bus: " + Arrays.toString(close) + "  (DOOR_COMMAND = close)");
        Thread.sleep(STEP_MS);
        send(close);
        System.out.println("   2. door side read the command from the bus");
        Thread.sleep(STEP_MS);
        waitFor(() -> state == 3);                                           // CLOSING seen on the bus
        waitFor(() -> state == 0);
        System.out.println("   4. read from the bus: DOOR_STATE = " + STATE[state] + "\n");
        Thread.sleep(STEP_MS);

        //test 3: open
        pause(); System.out.println("TEST 3: open the door");
        command(true);
        check("door opens", waitFor(() -> state == 2));
        System.out.println();

        //test 4: close is ignored while blocked
        pause(); System.out.println("TEST 4: close is ignored while the doorway is blocked");
        sensor(true);
        command(false);
        Thread.sleep(1000);
        check("door stays OPEN", state == 2);
        sensor(false);
        System.out.println();

        //test 5: blocked while closing -> reopen
        pause(); System.out.println("TEST 5: blocked while closing, the door reopens");
        boolean closing = false, reversed = false;
        for (int attempt = 1; attempt <= (client ? 3 : 1) && !reversed; attempt++) {
            if (client) {
                System.out.println("   >>> ok get ready" + (attempt > 1 ? " (try " + attempt + " of 3, almost there lol)" : "")
                        + ": click the Obstruction Sensor button the second the door starts closing, you got like 5 sec");
            }
            command(false);
            closing = waitFor(() -> state == 3);
            if (client) {
                waitFor(() -> blocked == 1, 15000);
            } else {
                Thread.sleep(HALF_WAY_MS);        // let the door close about half way so it is visible
                twin.placeObjectInDoorway(true);
            }
            reversed = waitFor(() -> state == 1 || state == 2, 3000);
            if (!reversed && client && attempt < 3) {
                System.out.println("   Too slow, the door finished closing first. Clearing and trying again.");
                sensor(false);
                command(true);
                waitFor(() -> state == 2);
            }
        }
        check("door starts closing", closing);
        check("door reverses", reversed);
        sensor(false);
        check("door ends fully open", waitFor(() -> state == 2));
        System.out.println();

        //test 6: open while closing -> reverse
        pause(); System.out.println("TEST 6: open while closing, the door reverses");
        command(false);
        check("door starts closing", waitFor(() -> state == 3));
        Thread.sleep(HALF_WAY_MS);        // let the door close about half way so it is visible
        command(true);
        check("door reverses", waitFor(() -> state == 1 || state == 2));
        check("door ends fully open", waitFor(() -> state == 2));
        System.out.println();

        //test 7: auto-close
        pause(); System.out.println(client ? "TEST 7: auto-close (waiting for the GUI door to close itself, no command sent)"
                                  : "TEST 7: auto-close (waiting about " + (int) HOLD_SEC + " seconds, no command sent)");
        check("door closes by itself", waitFor(() -> state == 0));
        System.out.println();

        System.out.println(failed == 0 ? "ALL TESTS PASSED" : failed + " CHECK(S) FAILED");
        socket.close();
        if (!client) {
            dash.stop();
            manager.stop();
            twin.stop();
        }
        System.exit(failed == 0 ? 0 : 1);
    }

    static void command(boolean open) throws java.io.IOException {
        byte[] msg = DoorBus.encode(DoorBus.TOPIC_DOOR_COMMAND, CAR, open ? 1 : 0);
        System.out.println("   published the command on the bus: " + Arrays.toString(msg) + "  (" + (open ? "open" : "close") + ")");
        send(msg);
    }

    static void check(String what, boolean ok) {
        System.out.println("   " + (ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) failed++;
    }

    static void pause() throws InterruptedException {
        Thread.sleep(PAUSE_MS);
    }

    /**
     * Blocks or clears the doorway. With our own door that is one call. With --client the door is in
     * the GUI, so we ask you to click its Obstruction Sensor button and wait until the bus reports the change.
     */
    static void sensor(boolean block) throws Exception {
        if (!client) {
            twin.placeObjectInDoorway(block);
            waitFor(() -> blocked == (block ? 1 : 0));
            return;
        }
        System.out.println("   >>> click the Obstruction Sensor button to " + (block ? "block" : "clear")
                + " the door. waiting for you...");
        if (!waitFor(() -> blocked == (block ? 1 : 0), 60000)) {
            System.out.println("  nothing changed on the bus after 60 sec, did you click it?");
            System.exit(1);
        }
        if (block && state == 0) {                       // the door closed itself while we waited
            System.out.println("   (the door had closed itself, reopening it)");
            command(true);
            waitFor(() -> state == 2);
        }
    }

    /** Waits up to 20 seconds. */
    static boolean waitFor(BooleanSupplier cond) throws InterruptedException {
        return waitFor(cond, 20000);
    }

    static boolean waitFor(BooleanSupplier cond, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(20);
        return cond.getAsBoolean();
    }

    static synchronized void send(byte[] msg) throws java.io.IOException {
        out.write(msg);
        out.flush();
    }
}
