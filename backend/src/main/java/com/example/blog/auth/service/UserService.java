package com.example.blog.auth.service;

import com.example.blog.auth.model.UserAccount;
import com.example.blog.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount createIfAbsent(String username, String rawPassword, String displayName, String role) {
        String key = normalize(username);
        return repository.findByUsername(key).orElseGet(() -> repository.save(new UserAccount(
                key, passwordEncoder.encode(rawPassword), displayName.trim(), normalizeRole(role), true)));
    }

    @Transactional
    public UserAccount configureBootstrap(String username, String rawPassword, String displayName, String role) {
        String key = normalize(username);
        String normalizedRole = normalizeRole(role);
        return repository.findByUsername(key)
                .map(existing -> {
                    String passwordHash = passwordEncoder.matches(rawPassword, existing.getPasswordHash())
                            ? existing.getPasswordHash() : passwordEncoder.encode(rawPassword);
                    return repository.update(new UserAccount(key, passwordHash, displayName.trim(), normalizedRole, true));
                })
                .orElseGet(() -> repository.save(new UserAccount(
                        key, passwordEncoder.encode(rawPassword), displayName.trim(), normalizedRole, true)));
    }

    public Optional<UserAccount> find(String username) {
        return repository.findByUsername(normalize(username));
    }

    public boolean matches(UserAccount user, String rawPassword) {
        return user != null && user.isEnabled() && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }

    @Transactional
    public void disableIfPresent(String username) {
        repository.disableByUsername(normalize(username));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
    }
}
