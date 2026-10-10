package co.zeal.marketplace.chat;

import java.time.Instant;

public record ChatMessageResponse(
        String id,
        String conversationId,
        String senderId,
        String senderName,
        String text,
        Instant sentAt) {

    static ChatMessageResponse from(PersistedChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSenderId(),
                message.getSenderName(),
                message.getText(),
                message.getSentAt());
    }
}
