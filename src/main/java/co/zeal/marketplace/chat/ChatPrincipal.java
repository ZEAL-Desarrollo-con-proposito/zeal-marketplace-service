package co.zeal.marketplace.chat;

import java.security.Principal;

public record ChatPrincipal(String userId, String role) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
