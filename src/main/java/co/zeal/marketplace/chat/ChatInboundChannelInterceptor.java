package co.zeal.marketplace.chat;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ChatInboundChannelInterceptor implements ChannelInterceptor {
    private static final Set<String> SUBSCRIPTIONS =
            Set.of("/user/queue/messages", "/user/queue/errors");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        String destination = headers.getDestination();
        if (headers.getCommand() == StompCommand.SUBSCRIBE
                && (destination == null || !SUBSCRIPTIONS.contains(destination))) {
            throw new MessagingException("Solo se permiten suscripciones a las colas privadas del chat.");
        }
        if (headers.getCommand() == StompCommand.SEND
                && !"/app/chat.send".equals(destination)) {
            throw new MessagingException("Solo se permiten envíos al canal privado del chat.");
        }
        return message;
    }
}
