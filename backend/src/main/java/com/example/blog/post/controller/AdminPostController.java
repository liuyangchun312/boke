package com.example.blog.post.controller;

import com.example.blog.common.ApiResponse;
import com.example.blog.common.PageResponse;
import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.Post;
import com.example.blog.post.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {
    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<PageResponse<Post>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag) {
        return ApiResponse.success(postService.search(page, size, keyword, category, tag, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<Post> detail(@PathVariable Long id) {
        return ApiResponse.success(postService.findAdminById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Post>> create(@Valid @RequestBody PostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(postService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Post> update(@PathVariable Long id, @Valid @RequestBody PostRequest request) {
        return ApiResponse.success(postService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ApiResponse.ok();
    }

    @PatchMapping("/{id}/publish")
    public ApiResponse<Post> publish(@PathVariable Long id) {
        return ApiResponse.success(postService.publish(id, true));
    }

    @PatchMapping("/{id}/unpublish")
    public ApiResponse<Post> unpublish(@PathVariable Long id) {
        return ApiResponse.success(postService.publish(id, false));
    }
}
