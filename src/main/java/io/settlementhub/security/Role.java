package io.settlementhub.security;

public enum Role {
    ADMIN,
    OPS,
    MERCHANT;

    public String authority() {
        return "ROLE_" + name();
    }
}
