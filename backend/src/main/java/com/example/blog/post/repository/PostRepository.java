package com.example.blog.post.repository;

import com.example.blog.post.model.Post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    List<Post> findAll();
    Optional<Post> findById(Long id);
    Optional<Post> findBySlug(String slug);
    Post save(Post post);
    Post incrementViewCount(Long id);
    void deleteById(Long id);
}
