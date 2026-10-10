package co.zeal.marketplace.chat;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ChatConversationService {

    private final ConversationRepository conversationRepository;
    private final PersistedChatMessageRepository messageRepository;

    public ChatConversationService(ConversationRepository conversationRepository,
                                   PersistedChatMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> history(String conversationId, String userId, String role) {
        ConversationParticipants participants = authorize(conversationId, userId, role);
        if (!conversationRepository.existsById(conversationId)) {
            return List.of();
        }
        return messageRepository.findByConversationIdOrderBySentAtAsc(participants.conversationId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    @Transactional
    public ChatMessageResponse send(String conversationId, String userId, String userName,
                                    String role, String text) {
        ConversationParticipants participants = authorize(conversationId, userId, role);
        if (text == null || text.isBlank() || text.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje debe contener entre 1 y 5000 caracteres.");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseGet(() -> conversationRepository.save(new Conversation(
                        conversationId,
                        participants.employeeId(),
                        participants.psychologistId(),
                        Instant.now())));
        PersistedChatMessage message = messageRepository.saveAndFlush(new PersistedChatMessage(
                UUID.randomUUID().toString(),
                conversation,
                userId,
                userName == null || userName.isBlank() ? userId : userName,
                text.trim(),
                Instant.now()));
        return ChatMessageResponse.from(message);
    }

    private ConversationParticipants authorize(String conversationId, String userId, String role) {
        String[] parts = conversationId == null ? new String[0] : conversationId.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de conversación inválido.");
        }
        boolean employee = "EMPLEADO".equals(role) || "EMPLOYEE".equals(role);
        boolean psychologist = "PSICOLOGO".equals(role) || "PSYCHOLOGIST".equals(role);
        boolean isParticipant = employee && parts[0].equals(userId)
                || psychologist && parts[1].equals(userId);
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes acceso a esta conversación.");
        }
        return new ConversationParticipants(conversationId, parts[0], parts[1]);
    }

    private record ConversationParticipants(String conversationId, String employeeId, String psychologistId) {}
}
