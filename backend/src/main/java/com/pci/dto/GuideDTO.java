package com.pci.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

public class GuideDTO {

    @Data
    public static class CreateRequest {
        private String city;
        private String title;
        private String content;
        private String tags;
        private Integer isOfficial;
    }

    @Data
    public static class ActionRequest {
        private String actionType;
    }

    @Data
    public static class GuideCard {
        private Long id;
        private String city;
        private String title;
        private String content;
        private String tags;
        private Integer isOfficial;
        private Double score;
        private Integer likeCount;
        private Integer favCount;
        private Integer coinCount;
        private Integer chargeCount;
        private Boolean liked;
        private Boolean favored;
        private LocalDateTime createdTime;
    }

    @Data
    public static class AiEvidence {
        private Long guideId;
        private String sourceType;
        private Double score;
        private String title;
        private String snippet;
    }

    @Data
    public static class ChatAnswer {
        private String reply;
        private List<AiEvidence> citations;
    }
}
