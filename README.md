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
| `Utilities/Message.java` | **Use** | The 8-byte message type. Build messages with `Message.of(topic, subtopic, value)`. Don't edit. |
| `Utilities/Protocol.java` | **Use** | The command codes (`SUBSCRIBE`, `GET`, `NO_MESSAGE` …), `isCommand`, and `readMessage` / `writeMessage`. Don't edit. |
| `SoftwareBus/Topics.java` | **Use** | Topic and subtopic numbers. Always use these constants, never raw numbers. Changes go through the bus team (or notify everyone if you need a change made). |
| `SoftwareBus/SoftwareBusServer.java` | **Start** | You create one, with a `MessageBus`, to run your bus. Don't edit. |
| `SoftwareBus/MessageBus.java` | **Start** | Passed to `SoftwareBusServer`; you never call it directly. Don't edit. |
| `TestHarness/THMain.java` | **Run / read (optional)** | A fake device that publishes random values and polls. You can use it for generating traffic; also a working example of the protocol. |
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
  byte 0    byte 1     bytes 2..7
+--------+----------+------------------------------+
| TOPIC  | SUBTOPIC |  BODY: 48-bit unsigned value |
+--------+----------+------------------------------+
                       (big-endian)
```

Byte 0 tells the bus what the frame is. 0xF0 and above is a command; anything lower is a topic, and the frame is published to every other connection. Commands carry their target topic in byte 1 and their target subtopic in the last byte of the body.

| You want to… | Java (`out` / `in` are the socket's streams) | Bus replies |
|---|---|---|
| **Publish** | `Protocol.writeMessage(out, Message.of(topic, subtopic, value))` | nothing |
| **Subscribe** | `Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, topic, subtopic))` | nothing |
| **Unsubscribe** | `Protocol.writeMessage(out, Message.of(Protocol.UNSUBSCRIBE, topic, subtopic))` | nothing |
| **Get next message** | `Protocol.writeMessage(out, Message.of(Protocol.GET, topic, subtopic))`<br>then `Message m = Protocol.readMessage(in)` | Exactly one message: the oldest queued one for that subscription, or a message whose `topic()` is `Protocol.NO_MESSAGE` if the queue is empty |
| Register | `Protocol.writeMessage(out, Message.of(Protocol.REGISTER, 0, 0))` | nothing (currently a no-op; you don't need it) |

### Notes

Notes on the current implementation. Please let us know if any of these are issues for you and if you need changes made.

- **GET is the only request that gets a reply.** Messages are pulled, not pushed: the bus queues messages for each of your subscriptions, and you collect them by sending GET until you get `NO_MESSAGE`.
- **`readMessage` returns `null` when the bus closes the connection.** Check for it before using the result.
- **Subscribe first.** The bus only queues messages for subscriptions that already exist. Anything published before you subscribed is gone.
- **You don't receive your own messages.** The bus delivers to every other connection. Give your control software and each emulated device their own socket, so they hear each other.
- **One thread per socket, or lock it.** A GET and its reply must not be split by another thread's request on the same socket. If several threads share a socket, wrap each request (and each GET with its read) in `synchronized`.
- **Set a read timeout** (`socket.setSoTimeout(5000)`) so a GET can never hang forever.
- **Each queue holds 1024 messages.** If you stop polling, newer messages are silently dropped once a queue is full.
- **Subscriptions belong to the connection.** If you reconnect, subscribe again.
- **Topics must be below `0xF0`.** The bus treats anything from `0xF0` up as a command, and drops an unknown one instead of publishing it. Check with `Protocol.isCommand(topic)` if your topic numbers are ever computed.

---

## 4. Your client code

### Example program: a door controller

```java
import SoftwareBus.MessageBus;
import SoftwareBus.SoftwareBusServer;
import SoftwareBus.Topics;
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
        Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, Topics.FIRE, Topics.ALL));
        Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, Topics.CAR_POSITION, elevator));

        while (true) {
            // 2. Drain each subscription: GET until NO_MESSAGE
            while (true) {
                Protocol.writeMessage(out, Message.of(Protocol.GET, Topics.FIRE, Topics.ALL));
                Message m = Protocol.readMessage(in);
                if (m == null) throw new EOFException("Bus closed the connection");
                if (m.topic() == Protocol.NO_MESSAGE) break;

                boolean alarmOn = m.value() == 1;
                // ... open/hold doors per fire procedure
            }

            while (true) {
                Protocol.writeMessage(out, Message.of(Protocol.GET, Topics.CAR_POSITION, elevator));
                Message m = Protocol.readMessage(in);
                if (m == null) throw new EOFException("Bus closed the connection");
                if (m.topic() == Protocol.NO_MESSAGE) break;

                long floor = m.value();
                // ... decide whether to open
            }

            // 3. Publish your own state whenever it changes
            // Protocol.writeMessage(out, Message.of(Topics.DOOR_STATE, elevator, 2));   // 2 = open

            Thread.sleep(100);
        }
    }
}
```

## 5. Known issues that we haven't fixed yet

These are tracked by the bus team.

1. **Subscribing with subtopic `ALL` only catches messages published with subtopic `ALL`.** It doesn't yet mean "any elevator". As a workaround you can subscribe to each elevator you care about (`ELEVATOR_1` … `ELEVATOR_4`) separately.
2. **The bus prints a stack trace whenever a connection closes.** You'll see one each time a test closes a socket. It's harmless and the bus keeps running.
3. **A bus can't be stopped.** It runs until your program exits. As a temporary workaround, start it once per program or test class, and use a separate port for tests.
