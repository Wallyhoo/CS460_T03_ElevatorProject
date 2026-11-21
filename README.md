# Software Bus — Integration Guide

The Software Bus is the shared messaging layer for the elevator system. Every team's software and every physical device talks to the rest of the system through the bus.

**How it will work:** each team runs its own Software Bus inside its own program, and connects its software and its physical devices to that one bus. For our final integration, hopefully we will only have to change what command line arguments we run the programs with.

This document covers:

1. Which files you need (and don't need)
2. Running the bus inside your program
3. The client protocol
4. Your client code, with an example program
5. Known issues that we haven't fixed yet

## 1. Which files you need (and don't need)

| File | You... | Notes |
|---|---|---|
| `Utilities/Message.java` | **Use** | The 8-byte message type. Build catalog messages with the named builders (Message.cabinStatus(...), Message.upCall(...), …) and read fields with payload(n). Use Message.of(...) only for bus commands (SUBSCRIBE, GET, …). Don’t edit. |
| `Utilities/Protocol.java` | **Use** | The command codes (`SUBSCRIBE`, `GET`, `NO_MESSAGE` …), `isCommand`, and `readMessage` / `writeMessage`. Don't edit. |
| `Utilities/Topics.java` | **Use** | Topic numbers for every message in the approved catalog, plus subtopic constants (ELEVATOR_1…ELEVATOR_4, NONE/ALL = 0). Always use these constants, never raw numbers. Changes go through the bus team (or notify everyone if you need a change made). |
| `SoftwareBus/SoftwareBusServer.java` | **Start** | You create one, with a `MessageBus`, to run your bus. Don't edit. |
| `SoftwareBus/MessageBus.java` | **Start** | Passed to `SoftwareBusServer`; you never call it directly. Don't edit. |
| `TestHarness/THMain.java` | **Run / read (optional)** |A fake device that publishes random values and polls. Good for generating traffic and as a protocol example, but its values don’t follow the catalog format, so don’t decode them as real messages. |
| `SoftwareBus/SoftwareBusClient.java` | **Don't touch** | Bus internals. This is the bus's side of your connection, not a client library. |
| `SoftwareBus/RouterLink.java` | **Don't touch** | Bus internals; not used by your bus. |
| `SoftwareBus/SBMain.java` | **Don't need** | The stand-alone bus launcher. You start the bus yourself instead. |
| `VirtualRouter/*` | **Don't need** | Used by the bus team for connecting buses. Not part of other teams' program. |

## 2. Running the bus inside your program

`SoftwareBusServer.start()` runs forever, so start it on its own thread at the top of your `main`:

```java
import SoftwareBus.MessageBus;
import SoftwareBus.SoftwareBusServer;
import java.io.IOException;

int busPort = 6000;

Thread busThread = new Thread(() -> {
    try {
        new SoftwareBusServer(busPort, new MessageBus()).start();
    } catch (IOException e) {
        System.err.println("Software Bus stopped: " + e);
    }
}, "software-bus");
busThread.setDaemon(true);
busThread.start();
```

Important notes:

- **Start it once**, before anything connects. Starting a second bus on the same port fails with "Address already in use".
- **It returns immediately**, before the bus is listening. Connect with a short retry loop (section 4) so your first connection doesn't get refused.
- **There's no stop.** The bus runs until your program exits.
- **Make the bus address configurable.** At integration various parts of our programs may need to connect to a bus somewhere else instead of starting their own. Take the bus host and port from your command-line arguments, to allow for flexibility later down the line.

```
  java ... DoorControllerMain                       # development: start our own bus on 6000
  java ... DoorControllerMain 192.168.8.10 6000     # integration: connect to an existing bus, don't start one
```

### Ports

| Use | Port |
|---|---|
| Your program's bus | 6000 |
| Your tests' bus | 6100 (so tests can run while your program is open) |

---

## 3. The client protocol

Each part of your system, namely the control software, as well as each device, opens a TCP socket to the bus. Everything on that socket, in both directions, is a sequence of 8-byte frames, each one a `Message`:

```
  byte 0    byte 1      bytes 2..7
+--------+----------+------+------+------+------+------+------+
| TOPIC  | SUBTOPIC |  P1  |  P2  |  P3  |  P4  |  P5  |  P6  |
+--------+----------+------+------+------+------+------+------+
                      payload byte 1 ... payload byte 6
```

Payload bytes are numbered from 1, as in the message catalog: payload byte 1 is the first byte after the subtopic (array index 2). For per-elevator topics the subtopic is the elevator number (1–4); building-wide topics (FIRE, UP_CALL, DOWN_CALL, CLEAR_REQUEST) use subtopic 0. Build messages with the Message builders, which put each field in the right byte and reject out-of-range values. Read fields with m.payload(n) (or m.flag(n) for yes/no fields). Don’t use Message.of(topic, subtopic, value) for catalog messages: it puts a small value in the last byte instead of payload byte 1.

| You want to… | Java (`out` / `in` are the socket's streams) | Bus replies |
|---|---|---|
| **Publish** | `Protocol.writeMessage(out, Message.cabinCall(elevator, floor))` (use the builder for your topic) | nothing |
| **Subscribe** | `Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, topic, subtopic))` | nothing |
| **Unsubscribe** | `Protocol.writeMessage(out, Message.of(Protocol.UNSUBSCRIBE, topic, subtopic))` | nothing |
| **Get next message** | `Protocol.writeMessage(out, Message.of(Protocol.GET, topic, subtopic))`<br>then `Message m = Protocol.readMessage(in)` | Exactly one message: the oldest queued one for that subscription, or a message whose `topic()` is `Protocol.NO_MESSAGE` if the queue is empty |
| **Register** | `Protocol.writeMessage(out, Message.of(Protocol.REGISTER, 0, 0))`<br>then `int id = Protocol.readMessage(in).subtopic()` | One REGISTER message with your ID (1–255) in the subtopic. IDs are unique within your bus; 0 means registration failed. Registering again on the same connection returns the same ID. Always read the reply right after sending REGISTER, or it will show up as the answer to your next GET. |

### Notes

Notes on the current implementation. Please let us know if any of these are issues for you and if you need changes made.

- **REGISTER and GET are the only requests that gets a reply.** Messages are pulled, not pushed: the bus queues messages for each of your subscriptions, and you collect them by sending GET until you get `NO_MESSAGE`.
- **An ID belongs to one connection** If a component disconnects and reconnects, it registers again and may get a different number. Freed IDs are reused lowest-first.
- **Two software buses can hand out the same ID** IDs are only relevant within one software bus, they are not coordinated globally via the virtual router.
- **`readMessage` returns `null` when the bus closes the connection.** Check for it before using the result.
- **Subscribe first.** The bus only queues messages for subscriptions that already exist. Anything published before you subscribed is gone.
- **You don't receive your own messages.** The bus delivers to every other connection. Give your control software and each emulated device their own socket, so they hear each other.
- **One thread per socket, or lock it.** A GET and its reply must not be split by another thread's request on the same socket. If several threads share a socket, wrap each request (and each GET with its read) in `synchronized`.
- **Set a read timeout** (`socket.setSoTimeout(5000)`) so a GET can never hang forever.
- **Each queue holds 1024 messages.** If you stop polling, newer messages are silently dropped once a queue is full.
- **Subscriptions belong to the connection.** If you reconnect, subscribe again.
- **Topics must be below `0xF0`.** The bus treats REGISTER, SUBSCRIBE, UNSUBSCRIBE and GET (0xF0–0xF3) as commands. Any other value, including 0xF4 (NO_MESSAGE) and above, is currently published to everyone like a normal message rather than rejected. Never publish with a topic of 0xF0 or higher. Always use the constants in Topics, and check with Protocol.isCommand(topic) if your topic numbers are ever computed.

---

## 4. Your client code

### Example program: a door controller

```java
import SoftwareBus.MessageBus;
import SoftwareBus.SoftwareBusServer;
import Utilities.Topics;
import Utilities.Message;
import Utilities.Protocol;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

public class DoorControllerMain {

    // Usage: DoorControllerMain [busHost busPort]
    //   no arguments: start our own bus on 6000 and connect to it
    //   host + port:  connect to an existing bus instead
    public static void main(String[] args) throws Exception {
        String busHost = "localhost";
        int busPort = 6000;

        if (args.length == 2) {
            busHost = args[0];
            busPort = Integer.parseInt(args[1]);
        } else {
            int port = busPort;
            Thread busThread = new Thread(() -> {
                try {
                    new SoftwareBusServer(port, new MessageBus()).start();
                } catch (IOException e) {
                    System.err.println("Software Bus stopped: " + e);
                }
            }, "software-bus");
            busThread.setDaemon(true);
            busThread.start();
        }

        // Connect, retrying for up to ~5 s while the bus starts
        Socket socket = null;
        for (int attempt = 1; socket == null; attempt++) {
            try {
                socket = new Socket(busHost, busPort);
            } catch (ConnectException e) {
                if (attempt == 50) throw e;
                Thread.sleep(100);
            }
        }
        socket.setSoTimeout(5000);
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        int elevator = Topics.ELEVATOR_1;

        // 1. Subscribe to everything you need before doing anything else
        Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, Topics.FIRE, Topics.NONE));
        Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, Topics.CABIN_STATUS, elevator));

        while (true) {
            // 2. Drain each subscription: GET until NO_MESSAGE
            while (true) {
                Protocol.writeMessage(out, Message.of(Protocol.GET, Topics.FIRE, Topics.NONE));
                Message m = Protocol.readMessage(in);
                if (m == null) throw new EOFException("Bus closed the connection");
                if (m.topic() == Protocol.NO_MESSAGE) break;

                boolean fireOn = m.flag(1);          // payload byte 1: 1 = fire, 0 = reset
                // ... open/hold doors per fire procedure
            }

            while (true) {
                Protocol.writeMessage(out, Message.of(Protocol.GET, Topics.CABIN_STATUS, elevator));
                Message m = Protocol.readMessage(in);
                if (m == null) throw new EOFException("Bus closed the connection");
                if (m.topic() == Protocol.NO_MESSAGE) break;

                boolean stopped = m.flag(1);         // payload byte 1
                int floor = m.payload(2);            // payload byte 2
                int direction = m.payload(3);        // payload byte 3: DIR_IDLE / DIR_UP / DIR_DOWN
                // ... decide whether to open
            }

            // 3. Publish your own state whenever it changes
            // Protocol.writeMessage(out, Message.doorStatus(elevator, true, false, false, false)); // fully open

            Thread.sleep(100);
        }
    }
}
```

## 5. Running and testing locally (ports and the test harness)

Use this to run the bus on its own, for example to generate traffic with the test harness or to test the bus without your own program.

Start the programs in this order (in practice the order doesn't matter, because buses reconnect to the router on their own, but this order gives the cleanest output):

1. **Virtual Router** (`VirtualRouter.VRMain [port]`). Listens on 5555 unless you give it a port.
2. **One or more Software Buses** (`SoftwareBus.SBMain <listenPort> [routerHost routerPort]`). Each bus needs its own port.
3. **Test Harness** (`TestHarness.THMain <busHost> <busPort>`). Connects to a **bus**, not the router.

`SBMain` examples:

```
java SoftwareBus.SBMain 6000                        stand-alone bus, no router
java SoftwareBus.SBMain 6000 localhost 5555         bus + router on this machine
java SoftwareBus.SBMain 6000 192.168.8.10 5555      router on another machine
java SoftwareBus.SBMain 6001 localhost 5555         a second bus: use a different listen port
```

A complete local run, with two buses and one test harness:

```
java VirtualRouter.VRMain
java SoftwareBus.SBMain 6000 localhost 5555
java SoftwareBus.SBMain 6001 localhost 5555
java TestHarness.THMain localhost 6000
```

The router port you give `SBMain` must match the port the router is listening on. If you start the router with a different port (for example `java VirtualRouter.VRMain 5000`), use that port for every bus too. The same rules apply when the programs run on different machines: replace `localhost` with the IP address of the machine running the router or bus.

**Important:** the test harness, and every other client, connects to a **Software Bus** port (6000, 6001, …), never to the Virtual Router. The router only talks to buses. If you point `THMain` at the router port it will connect, but the router never answers its REGISTER, so the harness times out after 5 seconds.

## 6. Known issues that we haven't fixed yet

These are tracked by the bus team.

1. **The bus prints a stack trace whenever a connection closes.** It's a `NullPointerException` from `SoftwareBusClient` ("message is null"), thrown on purpose when a client disconnects. You'll see one each time a test closes a socket. It's harmless: the client's ID and subscriptions are cleaned up and the bus keeps running.
2. **A bus can't be stopped.** It runs until your program exits. As a temporary workaround, start it once per program or test class, and use a separate port for tests.
3. **Topics 0xF4 and above are published instead of rejected.** Only REGISTER, SUBSCRIBE, UNSUBSCRIBE and GET are handled as commands. Always publish with the constants in `Topics`.
