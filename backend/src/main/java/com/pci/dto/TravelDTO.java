package com.pci.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

public class TravelDTO {

    @Data
    public static class RouteRequest {
        /** 城市名，如"上海"、"北京" */
        private String city;
        /** 出发地，如"南京路步行街" */
        private String origin;
        /** 目的地，如"人民广场" */
        private String destination;
    }

    @Data
    public static class RouteResult {
        private String origin;
        private String destination;
        private String city;
        private List<TransitRoute> routes;
        /** 出发地经纬度，格式 "lng,lat"，供前端地图打点 */
        private String originCoord;
        /** 目的地经纬度，格式 "lng,lat"，供前端地图打点 */
        private String destCoord;
    }

    @Data
    public static class ParseRequest {
        /** 自然语言输入，可中英混合，例如 "from my university to canton tower" */
        private String instruction;
        /** 可选：图片base64（不带 data: 前缀） */
        private String imageBase64;
        /** 可选：图片mime，如 image/png */
        private String imageMimeType;
    }

    @Data
    public static class ParseResult {
        private String summary;
        private RouteRequest draft;
        private RouteResult preview;
        private List<String> unresolved;
        private Map<String, String> meta;
    }

    @Data
    public static class ConfirmRequest {
        private RouteRequest route;
    }

    @Data
    public static class ConfirmResult {
        private RouteRequest route;
        private RouteResult result;
        private String message;
    }

    @Data
    public static class LocateRequest {
        /** 纬度 */
        private Double latitude;
        /** 经度 */
        private Double longitude;
        /** 可选：城市提示 */
        private String cityHint;
    }

    @Data
    public static class LocateResult {
        /** 逆地理得到的结构化地址名（可直接作为origin） */
        private String origin;
        /** 逆地理推断的城市 */
        private String city;
        /** 原始经纬度透传 */
        private Double latitude;
        private Double longitude;
    }

    @Data
    public static class TransitRoute {
        /** 预计耗时，如"25分钟" */
        private String duration;
        /** 总距离，如"3.2公里" */
        private String distance;
        /** 票价，如"3元" */
        private String cost;
        /** 换乘步骤，每一步是一段描述 */
        private List<String> steps;
        /**
         * 路线折线坐标点列表，每个元素为 "lng,lat"，
         * 按顺序连接即为实际路径，供前端地图绘制
         */
        private List<String> polyline;
    }
}
