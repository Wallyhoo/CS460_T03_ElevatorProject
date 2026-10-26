package SoftwareBus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The wire protocol, the shared contract between the SoftwareBusClient
 * and the SoftwareBusServer.
 *
 * <h2>Client &lt;-&gt; Bus (request / response)</h2>
 * Each request is a 1-byte command plus a fixed payload. The bus sends exactly
 * one response per request.
 * <pre>
 *  REQUEST                         RESPONSE
 *  REGISTER    (no payload)        REGISTERED + 4-byte id
 *  SUBSCRIBE   topic, subtopic     ACK
 *  UNSUBSCRIBE topic, subtopic     ACK
 *  PUBLISH     8-byte message      ACK
 *  GET         topic, subtopic     MESSAGE + 8-byte message   or   EMPTY
 *  (any)                           ERROR + 1-byte error code
 * </pre>
 *
 * <h2>Bus &lt;-&gt; Router</h2>
 * Raw 8-byte messages only, no command byte. The router forwards each message
 * to every other bus. The bus doesn't send a message it received from the router
 * back to the router, which prevents loops.
 */

public class Protocol {

    private Protocol() {}

    // Requests (client -> bus)
    public static final byte REGISTER    = 0x01;
    public static final byte SUBSCRIBE   = 0x02;
    public static final byte UNSUBSCRIBE = 0x03;
    public static final byte PUBLISH     = 0x04;
    public static final byte GET         = 0x05;

    // Responses (bus -> client)
    public static final byte ACK        = (byte) 0x80;
    public static final byte REGISTERED = (byte) 0x81;
    public static final byte MESSAGE    = (byte) 0x82;
    public static final byte EMPTY      = (byte) 0x83;
    public static final byte ERROR      = (byte) 0x8F;

    // Error codes (payload of ERROR)
    public static final byte ERR_NOT_REGISTERED     = 1;
    public static final byte ERR_UNKNOWN_COMMAND    = 2;
    public static final byte ERR_ALREADY_REGISTERED = 3;

    // Reads exactly one 8-byte message from a socket stream (blocks until it arrives)
    public static Message readMessage(DataInputStream in) throws IOException {
        byte[] wire = new byte[Message.SIZE];
        in.readFully(wire);
        return Message.fromBytes(wire);
    }

    // Writes a message's 8 bytes to a socket stream (caller flushes).
    public static void writeMessage(DataOutputStream out, Message m) throws IOException {
        out.write(m.toBytes());
    }

    // Human-readable description of an error code
    public static String errorName(int code) {
        switch (code) {
            case ERR_NOT_REGISTERED:     return "not registered (call register() first)";
            case ERR_UNKNOWN_COMMAND:    return "unknown command";
            case ERR_ALREADY_REGISTERED: return "already registered";
            default:                     return "error " + code;
        }
    }
}
