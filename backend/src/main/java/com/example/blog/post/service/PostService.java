package com.example.blog.post.service;

import com.example.blog.common.BadRequestException;
import com.example.blog.common.PageResponse;
import com.example.blog.common.ResourceNotFoundException;
import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.Post;
import com.example.blog.post.model.PostStatus;
import com.example.blog.post.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostService {
    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    public PageResponse<Post> search(int page, int size, String keyword, String category, String tag,
                                     boolean publishedOnly) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        String normalizedTag = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        List<Post> filtered = repository.findAll().stream()
                .filter(post -> !publishedOnly || post.getStatus() == PostStatus.PUBLISHED)
                .filter(post -> normalizedKeyword.isBlank()
                        || contains(post.getTitle(), normalizedKeyword)
                        || contains(post.getExcerpt(), normalizedKeyword)
                        || contains(post.getContent(), normalizedKeyword))
                .filter(post -> normalizedCategory.isBlank()
                        || normalizedCategory.equals(lower(post.getCategory())))
                .filter(post -> normalizedTag.isBlank() || post.getTags().stream()
                        .anyMatch(item -> normalizedTag.equals(lower(item))))
                .sorted(this::newestFirst)
                .toList();
        return paginate(filtered, page, size);
    }

    public Post findPublishedByIdOrSlug(String idOrSlug) {
        Post post = findByIdOrSlug(idOrSlug);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Published post not found");
        }
        return repository.incrementViewCount(post.getId());
    }

    public Post findPublishedBySlug(String slug) {
        Post post = repository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + slug));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Published post not found");
        }
        return repository.incrementViewCount(post.getId());
    }

    public Post findAdminById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
    }

    public Post requirePublishedById(Long id) {
        Post post = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Published post not found");
        }
        return post;
    }

    public Post create(PostRequest request) {
        Post post = new Post();
        post.setCreatedAt(Instant.now());
        apply(post, request, null);
        return repository.save(post);
    }

    public Post update(Long id, PostRequest request) {
        Post post = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        apply(post, request, id);
        return repository.save(post);
    }

    public void delete(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ResourceNotFoundException("Post not found: " + id);
        }
        repository.deleteById(id);
    }

    public Post publish(Long id, boolean published) {
        Post post = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + id));
        post.setStatus(published ? PostStatus.PUBLISHED : PostStatus.DRAFT);
        post.setPublishedAt(published ? (post.getPublishedAt() == null ? Instant.now() : post.getPublishedAt()) : null);
        post.setUpdatedAt(Instant.now());
        return repository.save(post);
    }

    public List<String> categories() {
        return repository.findAll().stream()
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                .map(Post::getCategory)
                .filter(value -> value != null && !value.isBlank()).map(String::trim)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public List<String> tags() {
        return repository.findAll().stream()
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                .flatMap(post -> post.getTags().stream())
                .filter(value -> value != null && !value.isBlank()).map(String::trim)
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private void apply(Post post, PostRequest request, Long currentId) {
        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        String category = request.getCategory() == null ? "" : request.getCategory().trim();
        if (title.isBlank() || category.isBlank() || request.getContent() == null || request.getContent().isBlank()) {
            throw new BadRequestException("title, content and category are required");
        }
        post.setTitle(title);
        post.setCategory(category);
        post.setContent(request.getContent());
        String excerpt = request.getExcerpt() == null ? "" : request.getExcerpt().trim();
        post.setExcerpt(excerpt.isBlank() ? excerptFrom(post.getContent()) : excerpt);
        post.setSlug(uniqueSlug(request.getSlug(), title, currentId, post.getSlug()));
        post.setTags(cleanTags(request.getTags()));
        post.setCoverImage(blankToNull(request.getCoverImage()));
        PostStatus status = request.getStatus() == null ? PostStatus.DRAFT : request.getStatus();
        post.setStatus(status);
        if (status == PostStatus.PUBLISHED && post.getPublishedAt() == null) post.setPublishedAt(Instant.now());
        if (status == PostStatus.DRAFT) post.setPublishedAt(null);
        post.setUpdatedAt(Instant.now());
    }

    private String uniqueSlug(String requested, String title, Long currentId, String existingSlug) {
        if ((requested == null || requested.isBlank()) && currentId != null
                && existingSlug != null && !existingSlug.isBlank()) {
            return existingSlug;
        }
        String base = requested == null || requested.isBlank() ? slugify(title) : slugify(requested);
        String candidate = base;
        int suffix = 2;
        while (repository.findBySlug(candidate).filter(existing -> !existing.getId().equals(currentId)).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private Post findByIdOrSlug(String idOrSlug) {
        if (idOrSlug == null || idOrSlug.isBlank()) throw new ResourceNotFoundException("Post not found");
        try {
            return repository.findById(Long.parseLong(idOrSlug))
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + idOrSlug));
        } catch (NumberFormatException ignored) {
            return repository.findBySlug(idOrSlug)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found: " + idOrSlug));
        }
    }

    private PageResponse<Post> paginate(List<Post> values, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        long requestedOffset = ((long) safePage - 1L) * safeSize;
        int from = requestedOffset >= values.size() ? values.size() : (int) requestedOffset;
        int to = Math.min(from + safeSize, values.size());
        int totalPages = values.isEmpty() ? 0 : (int) Math.ceil(values.size() / (double) safeSize);
        return new PageResponse<>(values.subList(from, to), safePage, safeSize, values.size(), totalPages);
    }

    private int newestFirst(Post left, Post right) {
        Instant a = left.getCreatedAt();
        Instant b = right.getCreatedAt();
        if (a == null && b == null) return compareIdDescending(left, right);
        if (a == null) return 1;
        if (b == null) return -1;
        int timeOrder = b.compareTo(a);
        return timeOrder != 0 ? timeOrder : compareIdDescending(left, right);
    }

    private int compareIdDescending(Post left, Post right) {
        if (left.getId() == null && right.getId() == null) return 0;
        if (left.getId() == null) return 1;
        if (right.getId() == null) return -1;
        return right.getId().compareTo(left.getId());
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private String lower(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT); }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        Set<String> clean = tags.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(clean);
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String excerptFrom(String content) {
        String plain = content.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
        return plain.length() > 160 ? plain.substring(0, 157) + "..." : plain;
    }

    private String slugify(String value) {
        String slug = value == null ? "" : value.toLowerCase(Locale.ROOT).trim()
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "post" : slug;
    }
}
