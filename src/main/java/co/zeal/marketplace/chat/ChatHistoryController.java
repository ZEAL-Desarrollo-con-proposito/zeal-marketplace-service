package co.zeal.marketplace.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chats/{conversationId}/mensajes")
public class ChatHistoryController {

    private final ChatConversationService chatService;

    public ChatHistoryController(ChatConversationService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public List<ChatMessageResponse> history(@PathVariable String conversationId,
                                             HttpServletRequest request) {
        return chatService.history(conversationId, userId(request), role(request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessageResponse send(@PathVariable String conversationId,
                                    @RequestBody SendChatMessageRequest body,
                                    HttpServletRequest request) {
        return chatService.send(conversationId, userId(request),
                (String) request.getAttribute("userFullName"), role(request),
                body == null ? null : body.text());
    }

    private String userId(HttpServletRequest request) {
        return (String) request.getAttribute("userId");
    }

    private String role(HttpServletRequest request) {
        return (String) request.getAttribute("userRole");
    }

    public record SendChatMessageRequest(String text) {}
}
