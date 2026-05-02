package com.pci.dto;

import com.pci.entity.Memo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;

public class MemoDTO {

    @Data
    public static class ParseRequest {
        @NotBlank(message = "指令不能为空")
        @Size(max = 200, message = "指令过长，请控制在200字以内")
        private String instruction;
    }

    @Data
    public static class ParseResult {
        private String summary;
        private List<MemoOp> ops;
        private List<String> unresolved;
        private List<Memo> queryResults;
    }

    @Data
    public static class MemoOp {
        @NotBlank(message = "操作类型不能为空")
        private String action;
        private Long matchedId;
        @Valid
        private Memo memo;
        private String message;
    }

    @Data
    public static class ConfirmRequest {
        @NotEmpty(message = "没有可执行的操作")
        @Valid
        private List<MemoOp> ops;
    }

    @Data
    public static class ConfirmResult {
        private Integer executed;
        private List<String> failed;
    }

    @Data
    public static class DraftMemo {
        private String title;
        private String content;
        private LocalDateTime remindTime;
    }
}
