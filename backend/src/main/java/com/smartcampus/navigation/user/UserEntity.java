package com.smartcampus.navigation.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("app_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String username;
    public String passwordHash;
    public String displayName;
    public String role;
    public String status;
    public LocalDateTime createdAt;
}

