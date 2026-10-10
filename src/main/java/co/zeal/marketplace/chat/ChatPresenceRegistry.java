package co.zeal.marketplace.chat;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ChatPresenceRegistry {
    private final ConcurrentMap<String, ChatPrincipal> sessions = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        if (event.getUser() instanceof ChatPrincipal principal) {
            String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
            if (sessionId != null) {
                sessions.put(sessionId, principal);
            }
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

    public boolean isConnected(String userId) {
        return sessions.values().stream().anyMatch(user -> user.userId().equals(userId));
    }

    public String connectedRole(String userId) {
        return sessions.values().stream()
                .filter(user -> user.userId().equals(userId))
                .map(ChatPrincipal::role)
                .findFirst()
                .orElse(null);
    }
}
