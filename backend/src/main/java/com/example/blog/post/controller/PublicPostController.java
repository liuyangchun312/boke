package com.example.blog.post.controller;

import com.example.blog.common.ApiResponse;
import com.example.blog.common.PageResponse;
import com.example.blog.post.model.Post;
import com.example.blog.post.service.PostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PublicPostController {
    private final PostService postService;

    public PublicPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Post>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {
        return ApiResponse.success(postService.search(page, size, keyword, category, tag, true));
    }

    @GetMapping("/slug/{slug}")
    public ApiResponse<Post> detailBySlug(@PathVariable String slug) {
        return ApiResponse.success(postService.findPublishedBySlug(slug));
    }

    @GetMapping("/{idOrSlug}")
    public ApiResponse<Post> detail(@PathVariable String idOrSlug) {
        return ApiResponse.success(postService.findPublishedByIdOrSlug(idOrSlug));
    }
}
