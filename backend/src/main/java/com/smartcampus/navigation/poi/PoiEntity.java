package com.smartcampus.navigation.poi;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("poi")
public class PoiEntity {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String name;
    public String category;
    public BigDecimal longitude;
    public BigDecimal latitude;
    public String locationText;
    public String openStatus;
    public String tags;
    public Boolean sheltered;
    public String remark;
    public Boolean enabled;
    public Integer mapRank;
    public String sourceUrl;
    public LocalDateTime updatedAt;
}
