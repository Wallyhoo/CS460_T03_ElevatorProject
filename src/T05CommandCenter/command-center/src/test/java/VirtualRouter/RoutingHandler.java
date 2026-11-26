package T05CommandCenter.command;

import Utilities.DeviceRegister;
import Utilities.Message;
import VirtualRouter.ClientHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingHandler {
    private final DeviceRegister deviceRegister = new DeviceRegister();

    private final Set<VirtualRouter.ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public void addClient(VirtualRouter.ClientHandler client) {
        clients.add(client);
    }

    public void removeClient(VirtualRouter.ClientHandler client) {
        clients.remove(client);
    }

    public void broadcast(Message message, VirtualRouter.ClientHandler sender) {

        byte[] data = message.toBytes();

        System.out.printf("Broadcasting message%n");

        for (ClientHandler client : clients) {
            if (client == sender) {
                continue;
            }

            try {
                client.send(data);
            } catch (IOException e) {
                removeClient(client);
                client.disconnect();
            }
        }
    }

    public int registerDevice() {
        return deviceRegister.register();
    }

    public void deregisterDevice(int id) {
        deviceRegister.deregister(id);
    }
}
