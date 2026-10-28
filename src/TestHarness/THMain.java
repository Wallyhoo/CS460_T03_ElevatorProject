package TestHarness;

import Utilities.Message;
import Utilities.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class THMain {
    private static final Random random = new Random();

    public static void main(String[] args) throws IOException, InterruptedException {

        if (args.length != 2) {
            System.out.println("Usage: java SoftwareBus.TestDevice <ip> <port>");
            return;
        }

        String ip = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(ip, port)) {

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // Prevent GET requests from blocking forever
            socket.setSoTimeout(5000);

            System.out.println("Connected to bus at " + ip + ":" + port);

            // Generate two distinct random topics
            int topic1 = random.nextInt(3) + 7;
            int topic2;

            do {
                topic2 = random.nextInt(3) + 7;
            } while (topic2 == topic1);

            // Generate random subtopics (1 or 2)
            int subtopic1 = random.nextInt(1) + 1;
            int subtopic2 = random.nextInt(1) + 1;

            int[][] subscriptions = {
                    {topic1, subtopic1},
                    {topic2, subtopic2}
            };

            // Subscribe to both topics
            for (int[] sub : subscriptions) {

                Protocol.writeMessage(out, Message.of(Protocol.SUBSCRIBE, sub[0], sub[1]));

                System.out.printf(
                        "SUBSCRIBE: Topic %d, Subtopic %d%n",
                        sub[0], sub[1]
                );
            }

            // Main testing loop
            while (true) {

                // Randomly select one of the subscriptions
                int[] selected = subscriptions[random.nextInt(2)];

                int topic = selected[0];
                int subtopic = selected[1];

                // Generate a random message value
                long value = random.nextLong() & Message.MAX_VALUE;

                Message message = Message.of(topic, subtopic, value);

                // Publish
                Protocol.writeMessage(out, message);

                System.out.printf("POST: Topic %d, Subtopic %d, Value %d%n", topic, subtopic, value);

                // Poll both subscribed queues
                for (int[] sub : subscriptions) {

                    Protocol.writeMessage(out, Message.of(Protocol.GET, sub[0], sub[1]));

                    // Read the bus's response
                    Message received = Protocol.readMessage(in);

                    if (received == null) {
                        throw new IOException("Bus disconnected");
                    }

                    if (received.topic() != Protocol.NO_MESSAGE) {

                        System.out.printf(
                                "RECEIVED: Topic %d, Subtopic %d, Value %d%n",
                                received.topic(),
                                received.subtopic(),
                                received.value()
                        );
                    }
                }

                // Wait 250-1000ms before next iteration
                Thread.sleep(10000 + random.nextInt(1001));
            }
        }
    }
}
