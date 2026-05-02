package com.pci.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class ChatDTO {

    @Data
    public static class ChatRequest {
        @NotBlank(message = "消息不能为空")
        @Size(max = 2000, message = "消息过长，请控制在2000字以内")
        private String message;

        @Size(max = 20, message = "历史消息过多")
        private List<Map<String, String>> history;
    }
}
