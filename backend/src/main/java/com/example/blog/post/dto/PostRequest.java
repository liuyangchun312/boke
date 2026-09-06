package com.example.blog.post.dto;

import com.example.blog.post.model.PostStatus;
import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class PostRequest {
    @NotBlank
    private String title;
    private String slug;
    private String excerpt;
    @NotBlank
    private String content;
    @NotBlank
    private String category;
    private List<String> tags = new ArrayList<>();
    private String coverImage;
    private PostStatus status = PostStatus.DRAFT;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags == null ? new ArrayList<>() : tags; }
    public String getCoverImage() { return coverImage; }
    public void setCoverImage(String coverImage) { this.coverImage = coverImage; }
    public PostStatus getStatus() { return status; }
    public void setStatus(PostStatus status) { this.status = status; }
}
