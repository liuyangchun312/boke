package com.example.blog.comment.controller;

import com.example.blog.comment.dto.CommentRequest;
import com.example.blog.comment.model.Comment;
import com.example.blog.comment.service.CommentService;
import com.example.blog.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {
    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<List<Comment>> list(@PathVariable Long postId) {
        return ApiResponse.success(commentService.findByPost(postId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Comment>> add(@PathVariable Long postId,
                                                     @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(commentService.add(postId, request)));
    }
}
