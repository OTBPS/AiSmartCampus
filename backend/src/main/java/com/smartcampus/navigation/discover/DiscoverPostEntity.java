package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("discover_post")
public class DiscoverPostEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String title;
    public String summary;
    public Long poiId;
    public String category;
    public String coverUrl;
    public String status;
    public LocalDateTime createdAt;
}

