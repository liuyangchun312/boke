package com.example.blog.comment.repository;

import com.example.blog.comment.model.Comment;

import java.util.List;

public interface CommentRepository {
    Comment save(Comment comment);
    List<Comment> findByPost(Long postId);
    List<Comment> findAll();
    boolean deleteById(Long id);
}
