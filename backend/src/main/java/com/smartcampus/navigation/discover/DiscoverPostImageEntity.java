package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("discover_post_image")
public class DiscoverPostImageEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long postId;
    public String imageUrl;
    public Integer sortOrder;
    public LocalDateTime createdAt;
}
