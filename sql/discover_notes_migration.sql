USE smart_campus_navigation;

ALTER TABLE discover_post
  ADD COLUMN user_id BIGINT NOT NULL DEFAULT 1 AFTER id,
  ADD COLUMN body TEXT NULL AFTER summary,
  ADD COLUMN rating INT NOT NULL DEFAULT 4 AFTER status,
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
  MODIFY poi_id BIGINT NOT NULL;

UPDATE discover_post
SET body = summary
WHERE body IS NULL OR body = '';

ALTER TABLE discover_post
  MODIFY body TEXT NOT NULL;

CREATE INDEX idx_discover_post_user ON discover_post (user_id);
CREATE INDEX idx_discover_post_poi ON discover_post (poi_id);
CREATE INDEX idx_discover_post_status_created ON discover_post (status, created_at);

CREATE TABLE discover_comment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content VARCHAR(600) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_discover_comment_post (post_id),
  INDEX idx_discover_comment_user (user_id)
);

CREATE TABLE discover_like (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_discover_like_user_post (user_id, post_id),
  INDEX idx_discover_like_post (post_id)
);

CREATE TABLE discover_favorite (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_discover_favorite_user_post (user_id, post_id),
  INDEX idx_discover_favorite_post (post_id)
);
