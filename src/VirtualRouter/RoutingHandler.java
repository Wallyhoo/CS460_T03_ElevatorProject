package VirtualRouter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoutingHandler {
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public void addClient(ClientHandler client) {
        clients.add(client);
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.send(message);
            }
        }
    }
}
