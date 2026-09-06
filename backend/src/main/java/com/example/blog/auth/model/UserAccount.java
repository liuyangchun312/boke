package com.example.blog.auth.model;

public class UserAccount {
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String role;
    private final boolean enabled;

    public UserAccount(String username, String passwordHash, String displayName, String role, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
