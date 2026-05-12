package com.coachsim.auth;

import java.security.Principal;

public record AuthPrincipal(Long id, String email, String role) implements Principal {
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }

    /** Used by Spring STOMP's user destination resolver: /user/{name}/queue/... */
    @Override
    public String getName() {
        return String.valueOf(id);
    }
}
