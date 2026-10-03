package com.example.blog.like.service;

import com.example.blog.common.BadRequestException;
import com.example.blog.like.dto.LikeState;
import com.example.blog.like.repository.LikeRepository;
import com.example.blog.post.service.PostService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class LikeService {
    private static final Pattern VISITOR_ID = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private final PostService posts;
    private final LikeRepository repository;

    public LikeService(PostService posts, LikeRepository repository) {
        this.posts = posts;
        this.repository = repository;
    }

    public LikeState find(Long postId, String visitorId) {
        validateVisitor(visitorId);
        posts.requirePublishedById(postId);
        return repository.findState(postId, visitorId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LikeState set(Long postId, String visitorId, boolean liked) {
        validateVisitor(visitorId);
        posts.requirePublishedById(postId);
        repository.setLiked(postId, visitorId, liked);
        return repository.findState(postId, visitorId);
    }

    @Transactional
    public void reset(Long postId) {
        posts.findAdminById(postId);
        repository.deleteByPost(postId);
    }

    private void validateVisitor(String visitorId) {
        if (visitorId == null || !VISITOR_ID.matcher(visitorId).matches()) {
            throw new BadRequestException("A valid X-Visitor-Id header is required");
        }
    }
}
