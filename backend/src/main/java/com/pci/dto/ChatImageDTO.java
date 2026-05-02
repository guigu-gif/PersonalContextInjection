package com.pci.dto;

import lombok.Data;

import java.util.Map;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ChatImageDTO {

    @Data
    public static class ImageRouteRequest {
        @Size(max = 300, message = "补充指令过长，请控制在300字以内")
        private String instruction;

        @NotBlank(message = "图片不能为空")
        @Size(max = 12_000_000, message = "图片数据过大")
        private String imageBase64;

        @Pattern(regexp = "^$|^image\\/(jpeg|jpg|png|gif|webp)$", message = "图片类型不支持")
        private String imageMimeType;
    }

    @Data
    public static class ImageRouteResult {
        private String module;
        private Boolean shouldJump;
        private String reason;
        private Double confidence;
        private Map<String, String> draft;
    }
}

