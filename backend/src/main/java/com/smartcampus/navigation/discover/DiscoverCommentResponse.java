package com.smartcampus.navigation.discover;

import java.time.LocalDateTime;

public class DiscoverCommentResponse {
    public Long id;
    public Long postId;
    public Long userId;
    public String authorName;
    public String content;
    public boolean owner;
    public LocalDateTime createdAt;
}
