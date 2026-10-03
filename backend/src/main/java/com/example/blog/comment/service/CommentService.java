package com.example.blog.comment.service;

import com.example.blog.comment.dto.CommentRequest;
import com.example.blog.comment.model.Comment;
import com.example.blog.comment.model.CommentStatus;
import com.example.blog.comment.repository.CommentRepository;
import com.example.blog.common.BadRequestException;
import com.example.blog.common.ResourceNotFoundException;
import com.example.blog.post.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        comment.setStatus(CommentStatus.PENDING);
        return repository.save(comment);
    }

    public List<Comment> findByPost(Long postId) {
        postService.requirePublishedById(postId);
        return repository.findApprovedByPost(postId);
    }

    public List<Comment> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Comment updateStatus(Long id, CommentStatus status) {
        if (status == null) throw new BadRequestException("Comment status is required");
        if (!repository.updateStatus(id, status)) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
        return requireById(id);
    }

    @Transactional
    public Comment reply(Long id, String content) {
        if (content == null || content.length() > 1000) {
            throw new BadRequestException("Reply content must be at most 1000 characters");
        }
        String reply = content.trim();
        Instant repliedAt = reply.isBlank() ? null : Instant.now();
        if (!repository.updateReply(id, reply.isBlank() ? null : reply, repliedAt)) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
        return requireById(id);
    }

    private Comment requireById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + id));
    }

    public void delete(Long id) {
        if (!repository.deleteById(id)) {
            throw new ResourceNotFoundException("Comment not found: " + id);
        }
    }
}
