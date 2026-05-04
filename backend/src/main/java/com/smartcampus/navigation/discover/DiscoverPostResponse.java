package com.smartcampus.navigation.discover;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DiscoverPostResponse {
    public Long id;
    public Long userId;
    public String authorName;
    public String title;
    public String summary;
    public String body;
    public Long poiId;
    public String poiName;
    public String poiCategory;
    public String poiLocationText;
    public String poiOpenStatus;
    public String poiTags;
    public String category;
    public String coverUrl;
    public List<DiscoverPostImageResponse> images = new ArrayList<>();
    public String status;
    public Integer rating;
    public long likeCount;
    public long favoriteCount;
    public long commentCount;
    public boolean liked;
    public boolean favorited;
    public boolean owner;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
    public List<DiscoverCommentResponse> comments = new ArrayList<>();
}
