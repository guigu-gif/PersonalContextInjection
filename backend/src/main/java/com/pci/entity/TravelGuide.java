package com.pci.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_travel_guide")
public class TravelGuide implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String city;
    private String title;
    private String content;
    private String tags;
    private Integer isOfficial;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
