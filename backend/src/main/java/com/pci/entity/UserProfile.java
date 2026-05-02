package com.pci.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("tb_user_profile")
public class UserProfile implements Serializable {
    @TableId
    private Long userId;
    private String identity;   // student / elder / general
    private String fontSize;   // normal / large / xlarge
    private String theme;      // default / elder / dark
    private String wallpaper;  // none / 预留key
    private LocalDateTime updatedTime;
}
