package com.example.blog.taxonomy.controller;

import com.example.blog.common.ApiResponse;
import com.example.blog.post.service.PostService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TaxonomyController {
    private final PostService postService;

    public TaxonomyController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.success(postService.categories());
    }

    @GetMapping("/tags")
    public ApiResponse<List<String>> tags() {
        return ApiResponse.success(postService.tags());
    }
}
