package co.zeal.marketplace.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_conversations")
public class Conversation {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, updatable = false)
    private String employeeId;

    @Column(nullable = false, updatable = false)
    private String psychologistId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Conversation() {}

    public Conversation(String id, String employeeId, String psychologistId, Instant createdAt) {
        this.id = id;
        this.employeeId = employeeId;
        this.psychologistId = psychologistId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getPsychologistId() {
        return psychologistId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
