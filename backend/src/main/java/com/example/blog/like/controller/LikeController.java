package com.example.blog.like.controller;

import com.example.blog.common.ApiResponse;
import com.example.blog.like.dto.LikeRequest;
import com.example.blog.like.dto.LikeState;
import com.example.blog.like.service.LikeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/likes")
public class LikeController {
    private final LikeService likes;

    public LikeController(LikeService likes) {
        this.likes = likes;
    }

    @GetMapping
    public ApiResponse<LikeState> state(@PathVariable Long postId,
                                       @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId) {
        return ApiResponse.success(likes.find(postId, visitorId));
    }

    @PutMapping
    public ApiResponse<LikeState> set(@PathVariable Long postId,
                                     @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
                                     @Valid @RequestBody LikeRequest request) {
        return ApiResponse.success(likes.set(postId, visitorId, request.liked()));
    }
}
