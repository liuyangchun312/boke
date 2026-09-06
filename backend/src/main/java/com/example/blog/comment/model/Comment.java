package com.example.blog.comment.model;

import java.time.Instant;

public class Comment {
    private Long id;
    private Long postId;
    private String author;
    private String content;
    private Instant createdAt;

    public Comment() {
    }

    public Comment(Long id, Long postId, String author, String content, Instant createdAt) {
        this.id = id;
        this.postId = postId;
        this.author = author;
        this.content = content;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public String getAuthor() { return author; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
