package com.smartcampus.navigation.discover;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("discover_comment")
public class DiscoverCommentEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long postId;
    public Long userId;
    public String content;
    public LocalDateTime createdAt;
}
