package com.smartcampus.navigation.feedback;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("feedback")
public class FeedbackEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long poiId;
    public String type;
    public String content;
    public String status;
    public String reviewNote;
    public LocalDateTime createdAt;
    public LocalDateTime reviewedAt;
}

