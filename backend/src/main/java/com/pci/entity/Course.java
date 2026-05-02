package com.pci.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("tb_course")
public class Course implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Integer weekday;    // 1-7
    private Integer startSlot;  // 1-12
    private Integer endSlot;
    private Integer weekStart;  // 开始周次，默认1
    private Integer weekEnd;    // 结束周次，默认20
    private Long semesterId;
    private String location;
    private String teacher;
    private String color;
}
