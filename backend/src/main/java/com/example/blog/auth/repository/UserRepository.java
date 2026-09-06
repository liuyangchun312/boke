package com.example.blog.auth.repository;

import com.example.blog.auth.model.UserAccount;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByUsername(String username);
    UserAccount save(UserAccount user);
    UserAccount update(UserAccount user);
    void disableByUsername(String username);
}
