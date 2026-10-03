package com.example.blog.comment.repository;

import com.example.blog.comment.model.Comment;
import com.example.blog.comment.model.CommentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment comment);
    Optional<Comment> findById(Long id);
    List<Comment> findByPost(Long postId);
    List<Comment> findApprovedByPost(Long postId);
    List<Comment> findAll();
    boolean updateStatus(Long id, CommentStatus status);
    boolean updateReply(Long id, String reply, Instant repliedAt);
    boolean deleteById(Long id);
}
