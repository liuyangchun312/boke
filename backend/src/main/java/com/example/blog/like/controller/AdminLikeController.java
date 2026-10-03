package com.example.blog.like.controller;

import com.example.blog.common.ApiResponse;
import com.example.blog.like.service.LikeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/posts/{postId}/likes")
public class AdminLikeController {
    private final LikeService likes;

    public AdminLikeController(LikeService likes) {
        this.likes = likes;
    }

    @DeleteMapping
    public ApiResponse<Void> reset(@PathVariable Long postId) {
        likes.reset(postId);
        return ApiResponse.success(null);
    }
}
