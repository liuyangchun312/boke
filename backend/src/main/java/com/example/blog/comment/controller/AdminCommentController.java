package com.example.blog.comment.controller;

import com.example.blog.comment.dto.CommentReplyRequest;
import com.example.blog.comment.dto.CommentStatusRequest;
import com.example.blog.comment.model.Comment;
import com.example.blog.comment.service.CommentService;
import com.example.blog.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {
    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ApiResponse<List<Comment>> list() {
        return ApiResponse.success(commentService.findAll());
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Comment> status(@PathVariable Long id, @Valid @RequestBody CommentStatusRequest request) {
        return ApiResponse.success(commentService.updateStatus(id, request.status()));
    }

    @PutMapping("/{id}/reply")
    public ApiResponse<Comment> reply(@PathVariable Long id, @Valid @RequestBody CommentReplyRequest request) {
        return ApiResponse.success(commentService.reply(id, request.content()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ApiResponse.success(null);
    }
}
