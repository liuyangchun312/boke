package com.example.blog.auth.controller;

import com.example.blog.auth.dto.LoginRequest;
import com.example.blog.auth.dto.LoginResponse;
import com.example.blog.auth.dto.UserView;
import com.example.blog.auth.model.UserAccount;
import com.example.blog.auth.security.JwtService;
import com.example.blog.auth.service.UserService;
import com.example.blog.common.ApiResponse;
import com.example.blog.common.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserAccount user = userService.find(request.username())
                .filter(candidate -> userService.matches(candidate, request.password()))
                .orElseThrow(() -> new BadRequestException("Invalid username or password"));
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        LoginResponse response = new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), toView(user));
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me(Authentication authentication) {
        UserAccount user = userService.find(authentication.getName())
                .orElseThrow(() -> new BadRequestException("User no longer exists"));
        return ApiResponse.success(toView(user));
    }

    private UserView toView(UserAccount user) {
        return new UserView(user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
