package co.zeal.marketplace.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_messages", indexes =
        @Index(name = "idx_chat_messages_conversation_time", columnList = "conversation_id, sent_at"))
public class PersistedChatMessage {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    private Conversation conversation;

    @Column(nullable = false, updatable = false)
    private String senderId;

    @Column(nullable = false, updatable = false)
    private String senderName;

    @Column(name = "message_text", nullable = false, updatable = false, length = 5000)
    private String text;

    @Column(nullable = false, updatable = false)
    private Instant sentAt;

    protected PersistedChatMessage() {}

    public PersistedChatMessage(String id, Conversation conversation, String senderId,
                                String senderName, String text, Instant sentAt) {
        this.id = id;
        this.conversation = conversation;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
