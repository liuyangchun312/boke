package com.example.blog.comment.service;

import com.example.blog.comment.dto.CommentRequest;
import com.example.blog.comment.model.Comment;
import com.example.blog.comment.repository.CommentRepository;
import com.example.blog.common.ResourceNotFoundException;
import com.example.blog.post.service.PostService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class CommentService {
    private final PostService postService;
    private final CommentRepository repository;

    public CommentService(PostService postService, CommentRepository repository) {
        this.postService = postService;
        this.repository = repository;
    }

    public Comment add(Long postId, CommentRequest request) {
        postService.requirePublishedById(postId);
        Comment comment = new Comment(null, postId,
                request.author().trim(), request.content().trim(), Instant.now());
        return repository.save(comment);
    }

    public List<Comment> findByPost(Long postId) {
        postService.requirePublishedById(postId);
        return repository.findByPost(postId);
    }

    public List<Comment> findAll() {
        return repository.findAll();
    }

    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
    }
}
