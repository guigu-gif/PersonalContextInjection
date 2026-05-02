package com.pci.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_memo")
public class Memo implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private LocalDateTime remindTime;
    private Integer reminded;
    private Integer source;
    private Integer status;
    private Integer aiExtracted;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
