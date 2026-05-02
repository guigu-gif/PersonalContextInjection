package com.pci.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_user_persona")
public class UserPersona implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String factKey;    // 事实类型：身份/偏好/关注/其他
    private String factValue;  // 事实内容
    private String source;     // manual / ai
    private LocalDateTime createdTime;
}
