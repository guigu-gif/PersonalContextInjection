package com.pci.dto;

import com.pci.entity.Course;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI操作课程表的数据结构
 */
public class CourseOpDTO {

    /** ai-parse 请求体 */
    @Data
    public static class ParseRequest {
        private String instruction; // 用户自然语言指令
    }

    /** 单条操作 */
    @Data
    public static class Op {
        private String action;      // CREATE / UPDATE / DELETE
        private Long matchedId;     // DELETE/UPDATE 时匹配到的课程id
        private Course course;      // 课程详情（用于前端展示）
        private Map<String, Object> newValues; // UPDATE 时要改成的值
    }

    /** ai-parse 返回体 */
    @Data
    public static class ParseResult {
        private List<Op> ops;
        private List<String> unresolved;
        private boolean slotsUpdated; // AI识图时是否同时更新了节次配置
    }

    /** ai-confirm 请求体中的单条操作（只传必要字段） */
    @Data
    public static class ConfirmOp {
        private String action;
        private Long matchedId;     // DELETE/UPDATE 时必填
        private Course course;      // CREATE/UPDATE 时必填
    }

    /** ai-confirm 请求体 */
    @Data
    public static class ConfirmRequest {
        private List<ConfirmOp> ops;
    }

    /** ai-confirm 返回体 */
    @Data
    public static class ConfirmResult {
        private int executed;
        private List<String> failed;
    }
}
