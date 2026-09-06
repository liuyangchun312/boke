CREATE TABLE posts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    excerpt VARCHAR(1000) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(255) NOT NULL,
    cover_image VARCHAR(1000),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    view_count BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_posts_slug UNIQUE (slug)
);

CREATE TABLE post_tags (
    post_id BIGINT NOT NULL,
    tag VARCHAR(255) NOT NULL,
    position INTEGER NOT NULL,
    PRIMARY KEY (post_id, position),
    CONSTRAINT fk_post_tags_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE TABLE comments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    author VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
);

CREATE TABLE users (
    username VARCHAR(100) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_posts_status_published ON posts(status, published_at);
CREATE INDEX idx_comments_post_created ON comments(post_id, created_at);
