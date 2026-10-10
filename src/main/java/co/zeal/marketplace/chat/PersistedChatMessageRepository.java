package co.zeal.marketplace.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersistedChatMessageRepository extends JpaRepository<PersistedChatMessage, String> {

    List<PersistedChatMessage> findByConversationIdOrderBySentAtAsc(String conversationId);
}
