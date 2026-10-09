package co.zeal.marketplace.support;

import co.zeal.marketplace.shared.security.AuthenticatedUser;
import co.zeal.marketplace.shared.security.Role;

public final class Users {

    public static final String NEXURA = "Nexura S.A.S.";

    private Users() {
    }

    public static AuthenticatedUser employee(String id) {
        return employee(id, NEXURA);
    }

    public static AuthenticatedUser employee(String id, String organization) {
        return new AuthenticatedUser(id, Role.EMPLEADO, "Empleado " + id, id + "@empresa.co", organization);
    }

    public static AuthenticatedUser admin(String organization) {
        return new AuthenticatedUser("admin-" + organization.hashCode(), Role.ADMIN, "Talento Humano",
                "th@empresa.co", organization);
    }
}
