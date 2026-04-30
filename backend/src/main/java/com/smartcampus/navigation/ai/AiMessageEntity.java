package com.smartcampus.navigation.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("ai_message")
public class AiMessageEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String question;
    public String intent;
    public String reply;
    public String toolCallsJson;
    public String mapActionsJson;
    public LocalDateTime createdAt;
}

