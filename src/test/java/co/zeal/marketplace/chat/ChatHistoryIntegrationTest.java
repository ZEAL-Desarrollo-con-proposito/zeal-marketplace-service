package co.zeal.marketplace.chat;

import co.zeal.marketplace.MarketplaceApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MarketplaceApplication.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:chat;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!"
})
@AutoConfigureMockMvc
class ChatHistoryIntegrationTest {

    private static final String SECRET =
            "ZealMVP2026ArchitectureSoftwareSecretKeyMinimum256Bits!!";
    private static final String CONVERSATION_ID = "employee-1:psychologist-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private PersistedChatMessageRepository messageRepository;
    @Autowired private ConversationRepository conversationRepository;

    @BeforeEach
    void clearHistory() {
        messageRepository.deleteAll();
        conversationRepository.deleteAll();
    }

    @Test
    void persistsMessagesAndReturnsChronologicalHistoryToEitherParticipant() throws Exception {
        mockMvc.perform(post("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("employee-1", "EMPLEADO", "Empleado Uno"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Hola, necesito conversar.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId").value(CONVERSATION_ID))
                .andExpect(jsonPath("$.senderId").value("employee-1"))
                .andExpect(jsonPath("$.senderName").value("Empleado Uno"))
                .andExpect(jsonPath("$.text").value("Hola, necesito conversar."));

        mockMvc.perform(post("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("psychologist-1", "PSICOLOGO", "Psicóloga Uno"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Estoy aquí para escucharte.\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("employee-1", "EMPLEADO", "Empleado Uno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].senderId").value("employee-1"))
                .andExpect(jsonPath("$[1].senderId").value("psychologist-1"));

        mockMvc.perform(get("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("psychologist-1", "PSICOLOGO", "Psicóloga Uno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        org.junit.jupiter.api.Assertions.assertEquals(1, conversationRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(2, messageRepository.count());
    }

    @Test
    void deniesHistoryAndSendingToNonParticipants() throws Exception {
        mockMvc.perform(get("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("intruder", "EMPLEADO", "Intruso")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("employee-1", "EMPLEADO", "Empleado Uno"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertEquals(0, messageRepository.count());
    }

    @Test
    void returnsEmptyHistoryForAuthorizedConversationUntilFirstMessage() throws Exception {
        mockMvc.perform(get("/api/chats/{conversationId}/mensajes", CONVERSATION_ID)
                        .header("Authorization", token("employee-1", "EMPLEADO", "Empleado Uno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String token(String subject, String role, String fullName) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .claim("fullName", fullName)
                .signWith(key)
                .compact();
    }
}
