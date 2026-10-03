package com.example.blog.like.repository;

import com.example.blog.like.dto.LikeState;

public interface LikeRepository {
    LikeState findState(Long postId, String visitorId);
    void setLiked(Long postId, String visitorId, boolean liked);
    void deleteByPost(Long postId);
}
