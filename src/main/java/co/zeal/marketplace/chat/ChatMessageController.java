package co.zeal.marketplace.chat;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
public class ChatMessageController {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatPresenceRegistry presenceRegistry;

    public ChatMessageController(SimpMessagingTemplate messagingTemplate,
                                 ChatPresenceRegistry presenceRegistry) {
        this.messagingTemplate = messagingTemplate;
        this.presenceRegistry = presenceRegistry;
    }

    @MessageMapping("/chat.send")
    public void send(ChatMessageRequest request, Principal principal) {
        ChatPrincipal sender = (ChatPrincipal) principal;
        if (request == null || request.recipientId() == null || request.recipientId().isBlank()
                || request.content() == null || request.content().isBlank()) {
            sendError(sender.userId(), "El destinatario y el mensaje son obligatorios.");
            return;
        }

        String recipientRole = presenceRegistry.connectedRole(request.recipientId());
        if (recipientRole == null) {
            sendError(sender.userId(), "El destinatario no está conectado.");
            return;
        }
        if (sender.role().equals(recipientRole)) {
            sendError(sender.userId(), "El chat solo permite comunicación empleado-psicólogo.");
            return;
        }

        ChatMessage message = new ChatMessage(sender.userId(), sender.role(),
                request.recipientId(), request.content(), Instant.now());
        messagingTemplate.convertAndSendToUser(request.recipientId(), "/queue/messages", message);
    }

    private void sendError(String userId, String message) {
        messagingTemplate.convertAndSendToUser(userId, "/queue/errors", new ChatError(message));
    }

    public record ChatMessageRequest(String recipientId, String content) {}
    public record ChatMessage(String senderId, String senderRole, String recipientId,
                              String content, Instant sentAt) {}
    public record ChatError(String message) {}
}
