package com.example.blog.auth.repository;

import com.example.blog.auth.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcTemplate jdbc;

    public JdbcUserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> users = jdbc.query("""
                        SELECT username, password_hash, display_name, role, enabled
                        FROM users WHERE username = ?
                        """,
                (rs, rowNum) -> new UserAccount(rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("display_name"),
                        rs.getString("role"), rs.getBoolean("enabled")), username);
        return users.stream().findFirst();
    }

    @Override
    public UserAccount save(UserAccount user) {
        jdbc.update("""
                        INSERT INTO users (username, password_hash, display_name, role, enabled)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                user.getUsername(), user.getPasswordHash(), user.getDisplayName(),
                user.getRole(), user.isEnabled());
        return user;
    }

    @Override
    public UserAccount update(UserAccount user) {
        int changed = jdbc.update("""
                        UPDATE users
                        SET password_hash = ?, display_name = ?, role = ?, enabled = ?
                        WHERE username = ?
                        """,
                user.getPasswordHash(), user.getDisplayName(), user.getRole(),
                user.isEnabled(), user.getUsername());
        if (changed == 0) throw new org.springframework.dao.EmptyResultDataAccessException(1);
        return user;
    }

    @Override
    public void disableByUsername(String username) {
        jdbc.update("UPDATE users SET enabled = FALSE WHERE username = ?", username);
    }
}
