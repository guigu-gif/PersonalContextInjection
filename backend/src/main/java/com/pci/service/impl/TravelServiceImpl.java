package com.pci.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pci.dto.Result;
import com.pci.dto.TravelDTO;
import com.pci.service.ITravelService;
import com.pci.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TravelServiceImpl implements ITravelService {

    @Value("${gaode.api-key}")
    private String apiKey;

    @Value("${gaode.geocode-url}")
    private String geocodeUrl;

    @Value("${gaode.transit-url}")
    private String transitUrl;

    @Value("${gaode.inputtips-url}")
    private String inputtipsUrl;
    @Value("${gaode.regeo-url:http://restapi.amap.com/v3/geocode/regeo}")
    private String regeoUrl;

    @Value("${zhipu.chat-url:}")
    private String zhipuChatUrl;

    @Value("${zhipu.chat-model:}")
    private String zhipuChatModel;

    @Value("${zhipu.api-key:}")
    private String zhipuApiKey;

    @Value("${vision.api-url:}")
    private String visionApiUrl;

    @Value("${vision.model:}")
    private String visionModel;

    @Value("${vision.api-key:}")
    private String visionApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Result planRoute(TravelDTO.RouteRequest request) {
        if (request == null || isBlank(request.getCity())
                || isBlank(request.getOrigin()) || isBlank(request.getDestination())) {
            return Result.fail("城市、出发地、目的地不能为空");
        }

        try {
            // 第一步：把地名转成经纬度坐标
            GeocodeResult originGeo = geocode(request.getCity(), request.getOrigin());
            GeocodeResult destGeo = geocode(request.getCity(), request.getDestination());
            String originCoord = originGeo.location;
            String destCoord = destGeo.location;

            if (originCoord == null) {
                return Result.fail("无法识别出发地：" + request.getOrigin() + "（" + originGeo.reason + "）");
            }
            if (destCoord == null) {
                return Result.fail("无法识别目的地：" + request.getDestination() + "（" + destGeo.reason + "）");
            }

            // 第二步：查公交/地铁路线
            List<TravelDTO.TransitRoute> routes = queryTransit(originCoord, destCoord, request.getCity());

            TravelDTO.RouteResult result = new TravelDTO.RouteResult();
            result.setOrigin(request.getOrigin());
            result.setDestination(request.getDestination());
            result.setCity(request.getCity());
            result.setRoutes(routes);
            result.setOriginCoord(originCoord);
            result.setDestCoord(destCoord);

            return Result.ok(result);

        } catch (Exception e) {
            log.error("[Travel] planRoute failed", e);
            return Result.fail("路线查询失败，请稍后重试");
        }
    }

    @Override
    public Result aiParse(TravelDTO.ParseRequest request) {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[AUDIT][TRAVEL][PARSE][START] userId={}, hasInstruction={}, hasImage={}",
                userId, request != null && !isBlank(request.getInstruction()),
                request != null && !isBlank(request.getImageBase64()));
        TravelDTO.ParseResult result = new TravelDTO.ParseResult();
        result.setUnresolved(new ArrayList<>());
        result.setMeta(new HashMap<>());

        if (request == null) return Result.fail("请求不能为空");
        String instruction = safe(request.getInstruction());
        boolean hasImage = !isBlank(request.getImageBase64());
        if (isBlank(instruction) && !hasImage) {
            log.warn("[AUDIT][TRAVEL][PARSE][REJECT] userId={}, reason=empty_instruction_and_image", userId);
            return Result.fail("指令或图片至少提供一个");
        }

        try {
            TravelDTO.RouteRequest draft = hasImage ? parseByImage(request) : parseByText(instruction);
            if (draft == null || isBlank(draft.getCity()) || isBlank(draft.getOrigin()) || isBlank(draft.getDestination())) {
                result.getUnresolved().add("未能解析出完整的城市/出发地/目的地，请手动补充");
                log.info("[AUDIT][TRAVEL][PARSE][END] userId={}, mode={}, unresolved={}",
                        userId, hasImage ? "image" : "text", result.getUnresolved().size());
                return Result.ok(result);
            }

            Result routeRes = planRoute(draft);
            if (!Boolean.TRUE.equals(routeRes.getSuccess())) {
                result.setDraft(draft);
                result.getUnresolved().add(routeRes.getErrorMsg());
                log.info("[AUDIT][TRAVEL][PARSE][END] userId={}, mode={}, routePreview=failed, reason={}",
                        userId, hasImage ? "image" : "text", routeRes.getErrorMsg());
                return Result.ok(result);
            }

            result.setSummary("已生成路线预览，请确认后执行");
            result.setDraft(draft);
            result.setPreview((TravelDTO.RouteResult) routeRes.getData());
            result.getMeta().put("mode", hasImage ? "image" : "text");
            log.info("[AUDIT][TRAVEL][PARSE][END] userId={}, mode={}, routePreview=ok, routes={}",
                    userId, hasImage ? "image" : "text",
                    result.getPreview() == null || result.getPreview().getRoutes() == null ? 0 : result.getPreview().getRoutes().size());
            return Result.ok(result);
        } catch (Exception e) {
            log.error("[Travel] aiParse failed", e);
            log.error("[AUDIT][TRAVEL][PARSE][ERROR] userId={}, err={}", userId, e.getMessage(), e);
            return Result.fail("AI解析失败，请稍后重试");
        }
    }

    @Override
    public Result aiConfirm(TravelDTO.ConfirmRequest request) {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        log.info("[AUDIT][TRAVEL][CONFIRM][START] userId={}, hasRoute={}", userId, request != null && request.getRoute() != null);
        if (request == null || request.getRoute() == null) {
            log.warn("[AUDIT][TRAVEL][CONFIRM][REJECT] userId={}, reason=empty_route", userId);
            return Result.fail("确认数据不能为空");
        }
        log.info("[AUDIT][TRAVEL][EXECUTE][TRY] userId={}, city={}, origin={}, destination={}",
                userId,
                safe(request.getRoute().getCity()),
                safe(request.getRoute().getOrigin()),
                safe(request.getRoute().getDestination()));
        Result routeRes = planRoute(request.getRoute());
        if (!Boolean.TRUE.equals(routeRes.getSuccess())) return routeRes;

        TravelDTO.ConfirmResult result = new TravelDTO.ConfirmResult();
        result.setRoute(request.getRoute());
        result.setResult((TravelDTO.RouteResult) routeRes.getData());
        result.setMessage("路线已确认，可用于后续导航或AI建议生成");
        log.info("[AUDIT][TRAVEL][EXECUTE][OK] userId={}, routes={}",
                userId, result.getResult() == null || result.getResult().getRoutes() == null ? 0 : result.getResult().getRoutes().size());
        log.info("[AUDIT][TRAVEL][CONFIRM][END] userId={}, success=true", userId);
        return Result.ok(result);
    }

    @Override
    public Result locate(TravelDTO.LocateRequest request) {
        Long userId = UserHolder.getUser() != null ? UserHolder.getUser().getId() : null;
        if (request == null || request.getLatitude() == null || request.getLongitude() == null) {
            log.warn("[AUDIT][TRAVEL][LOCATE][REJECT] userId={}, reason=empty_lat_lng", userId);
            return Result.fail("定位参数不能为空");
        }
        double lat = request.getLatitude();
        double lng = request.getLongitude();
        if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            log.warn("[AUDIT][TRAVEL][LOCATE][REJECT] userId={}, lat={}, lng={}, reason=out_of_range", userId, lat, lng);
            return Result.fail("定位参数超出范围");
        }
        log.info("[AUDIT][TRAVEL][LOCATE][START] userId={}, lat={}, lng={}", userId, lat, lng);
        try {
            RegeoResult regeoResult = reverseGeocode(lat, lng, userId);
            if (!regeoResult.success) {
                log.warn("[AUDIT][TRAVEL][LOCATE][FAIL] userId={}, reason={}", userId, regeoResult.reason);
                return Result.fail("定位解析失败：" + regeoResult.reason);
            }

            TravelDTO.LocateResult result = new TravelDTO.LocateResult();
            result.setOrigin(regeoResult.origin);
            result.setCity(regeoResult.city);
            result.setLatitude(lat);
            result.setLongitude(lng);
            log.info("[AUDIT][TRAVEL][LOCATE][END] userId={}, city={}, origin={}", userId, regeoResult.city, regeoResult.origin);
            return Result.ok(result);
        } catch (Exception e) {
            log.error("[AUDIT][TRAVEL][LOCATE][ERROR] userId={}, err={}", userId, e.getMessage(), e);
            return Result.fail("定位解析异常，请稍后重试");
        }
    }

    private RegeoResult reverseGeocode(double lat, double lng, Long userId) {
        List<String> candidates = new ArrayList<>();
        candidates.add(regeoUrl);
        if (regeoUrl.startsWith("http://")) {
            candidates.add("https://" + regeoUrl.substring("http://".length()));
        } else if (regeoUrl.startsWith("https://")) {
            candidates.add("http://" + regeoUrl.substring("https://".length()));
        }
        String defaultHttps = "https://restapi.amap.com/v3/geocode/regeo";
        String defaultHttp = "http://restapi.amap.com/v3/geocode/regeo";
        if (!candidates.contains(defaultHttps)) candidates.add(defaultHttps);
        if (!candidates.contains(defaultHttp)) candidates.add(defaultHttp);

        String lastReason = "未知错误";
        for (String baseUrl : candidates) {
            RegeoResult one = reverseGeocodeOnce(baseUrl, lat, lng);
            if (one.success) {
                log.info("[AUDIT][TRAVEL][LOCATE][TRY_OK] userId={}, baseUrl={}", userId, baseUrl);
                return one;
            }
            lastReason = one.reason;
            log.warn("[AUDIT][TRAVEL][LOCATE][TRY_FAIL] userId={}, baseUrl={}, reason={}", userId, baseUrl, one.reason);
        }
        return RegeoResult.fail(lastReason);
    }

    private RegeoResult reverseGeocodeOnce(String baseUrl, double lat, double lng) {
        try {
            String location = lng + "," + lat;
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("key", apiKey)
                    .queryParam("location", location)
                    .queryParam("extensions", "base")
                    .queryParam("radius", 1000)
                    .queryParam("output", "json")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);
            if (!"1".equals(root.path("status").asText())) {
                String info = root.path("info").asText("");
                String infocode = root.path("infocode").asText("");
                return RegeoResult.fail("高德返回失败 info=" + info + ", infocode=" + infocode);
            }
            JsonNode regeo = root.path("regeocode");
            JsonNode comp = regeo.path("addressComponent");
            String city = safe(comp.path("city").asText(""));
            if (isBlank(city)) {
                city = safe(comp.path("province").asText(""));
            }
            String poi = "";
            JsonNode pois = regeo.path("pois");
            if (pois.isArray() && pois.size() > 0) {
                poi = safe(pois.get(0).path("name").asText(""));
            }
            String formatted = safe(regeo.path("formatted_address").asText(""));
            String origin = !isBlank(poi) ? poi : formatted;
            if (isBlank(origin)) {
                origin = "当前位置";
            }
            return RegeoResult.success(city, origin);
        } catch (Exception e) {
            return RegeoResult.fail("请求异常：" + e.getClass().getSimpleName());
        }
    }

    private TravelDTO.RouteRequest parseByText(String instruction) throws Exception {
        String prompt = "你是出行信息抽取助手。请从用户输入中提取城市、出发地、目的地。"
                + "只输出JSON，不要解释。格式：{\"city\":\"\",\"origin\":\"\",\"destination\":\"\"}。"
                + "若缺失字段请用空字符串。";

        Map<String, Object> body = new HashMap<>();
        body.put("model", zhipuChatModel);
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new HashMap<>();
        sys.put("role", "system");
        sys.put("content", prompt);
        messages.add(sys);
        Map<String, String> user = new HashMap<>();
        user.put("role", "user");
        user.put("content", instruction);
        messages.add(user);
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(zhipuApiKey);
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(zhipuChatUrl, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").get(0).path("message").path("content").asText("");
        String json = extractJson(content);
        if (isBlank(json)) return null;

        JsonNode parsed = objectMapper.readTree(json);
        TravelDTO.RouteRequest req = new TravelDTO.RouteRequest();
        req.setCity(safe(parsed.path("city").asText("")));
        req.setOrigin(safe(parsed.path("origin").asText("")));
        req.setDestination(safe(parsed.path("destination").asText("")));
        return req;
    }

    private TravelDTO.RouteRequest parseByImage(TravelDTO.ParseRequest request) throws Exception {
        String mime = isBlank(request.getImageMimeType()) ? "image/jpeg" : request.getImageMimeType();
        String prompt = "识别图片中的出行关键信息，提取城市、出发地、目的地。"
                + "只输出JSON：{\"city\":\"\",\"origin\":\"\",\"destination\":\"\"}。"
                + "若只识别到目的地，请destination填值，其他为空。";

        Map<String, Object> imageUrl = new HashMap<>();
        imageUrl.put("url", "data:" + mime + ";base64," + request.getImageBase64());
        Map<String, Object> imageContent = new HashMap<>();
        imageContent.put("type", "image_url");
        imageContent.put("image_url", imageUrl);
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", java.util.Arrays.asList(imageContent, textContent));

        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "你是出行识图助手。");

        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("messages", java.util.Arrays.asList(systemMsg, userMsg));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.setBearerAuth(visionApiKey);
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        org.springframework.http.ResponseEntity<String> response = restTemplate.postForEntity(
                visionApiUrl + "/chat/completions", entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").get(0).path("message").path("content").asText("");
        String json = extractJson(content);
        if (isBlank(json)) return null;

        JsonNode parsed = objectMapper.readTree(json);
        TravelDTO.RouteRequest req = new TravelDTO.RouteRequest();
        req.setCity(safe(parsed.path("city").asText("")));
        req.setOrigin(safe(parsed.path("origin").asText("")));
        req.setDestination(safe(parsed.path("destination").asText("")));
        return req;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractJson(String content) {
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /**
     * 地理编码：地名 → 经纬度字符串 "lng,lat"
     */
    private GeocodeResult geocode(String city, String address) {
        // 依次尝试：原城市 -> 补“市”后缀 -> 不带城市（全国）
        List<String> cityCandidates = new ArrayList<>();
        if (!isBlank(city)) {
            cityCandidates.add(city.trim());
            if (!city.trim().endsWith("市")) {
                cityCandidates.add(city.trim() + "市");
            }
        }
        cityCandidates.add(null);

        String lastReason = "未知错误";
        for (String cityCandidate : cityCandidates) {
            GeocodeResult result = geocodeOnce(cityCandidate, address);
            if (result.location != null) {
                return result;
            }
            lastReason = result.reason;
        }
        // geocode 全失败后，再尝试输入提示接口兜底
        GeocodeResult tipsResult = inputTipsGeocode(city, address);
        if (tipsResult.location != null) {
            return tipsResult;
        }
        return GeocodeResult.fail(lastReason + "；兜底失败：" + tipsResult.reason);
    }

    private GeocodeResult geocodeOnce(String city, String address) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(geocodeUrl)
                    .queryParam("key", apiKey)
                    .queryParam("address", address);
            if (!isBlank(city)) {
                builder.queryParam("city", city);
            }
            URI uri = builder.build().encode(StandardCharsets.UTF_8).toUri();
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();
            String info = root.path("info").asText("");
            String infocode = root.path("infocode").asText("");
            if (!"1".equals(status)) {
                String reason = "高德返回失败 info=" + info + ", infocode=" + infocode;
                log.warn("[Travel] geocode not success, city={}, address={}, {}", city, address, reason);
                return GeocodeResult.fail(reason);
            }

            JsonNode geocodes = root.path("geocodes");
            if (!geocodes.isArray() || geocodes.size() == 0) {
                String reason = "未找到匹配地点";
                log.warn("[Travel] geocode empty result, city={}, address={}", city, address);
                return GeocodeResult.fail(reason);
            }

            String location = geocodes.get(0).path("location").asText();
            if (isBlank(location)) {
                String reason = "返回坐标为空";
                log.warn("[Travel] geocode location empty, city={}, address={}", city, address);
                return GeocodeResult.fail(reason);
            }
            return GeocodeResult.success(location);

        } catch (Exception e) {
            log.error("[Travel] geocode failed for: {}", address, e);
            return GeocodeResult.fail("请求异常：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 输入提示兜底：keywords + city，取首条 location
     */
    private GeocodeResult inputTipsGeocode(String city, String address) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(inputtipsUrl)
                    .queryParam("key", apiKey)
                    .queryParam("keywords", address)
                    .queryParam("datatype", "all")
                    .queryParam("citylimit", "false");
            if (!isBlank(city)) {
                builder.queryParam("city", city);
            }
            URI uri = builder.build().encode(StandardCharsets.UTF_8).toUri();
            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            String status = root.path("status").asText();
            String info = root.path("info").asText("");
            String infocode = root.path("infocode").asText("");
            if (!"1".equals(status)) {
                String reason = "inputtips失败 info=" + info + ", infocode=" + infocode;
                log.warn("[Travel] inputtips not success, city={}, address={}, {}", city, address, reason);
                return GeocodeResult.fail(reason);
            }

            JsonNode tips = root.path("tips");
            if (!tips.isArray() || tips.size() == 0) {
                return GeocodeResult.fail("inputtips无候选结果");
            }
            for (JsonNode tip : tips) {
                String location = tip.path("location").asText();
                if (!isBlank(location) && location.contains(",")) {
                    return GeocodeResult.success(location);
                }
            }
            return GeocodeResult.fail("inputtips候选坐标为空");
        } catch (Exception e) {
            log.error("[Travel] inputtips failed for: {}", address, e);
            return GeocodeResult.fail("inputtips请求异常：" + e.getClass().getSimpleName());
        }
    }

    /**
     * 查询公交/地铁路线，返回最多3条备选方案
     */
    private List<TravelDTO.TransitRoute> queryTransit(String origin, String destination, String city) {
        List<TravelDTO.TransitRoute> routes = new ArrayList<>();
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(transitUrl)
                    .queryParam("key", apiKey)
                    .queryParam("origin", origin)
                    .queryParam("destination", destination)
                    .queryParam("city", city)
                    .queryParam("output", "json")
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUri();

            String response = restTemplate.getForObject(uri, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (!"1".equals(root.path("status").asText())) return routes;

            JsonNode transits = root.path("route").path("transits");
            if (!transits.isArray()) return routes;

            // 最多取3条路线
            int limit = Math.min(transits.size(), 3);
            for (int i = 0; i < limit; i++) {
                JsonNode transit = transits.get(i);
                TravelDTO.TransitRoute route = parseTransit(transit);
                routes.add(route);
            }

        } catch (Exception e) {
            log.error("[Travel] queryTransit failed", e);
        }
        return routes;
    }

    /**
     * 把高德返回的单条路线解析成我们自己的结构
     */
    private TravelDTO.TransitRoute parseTransit(JsonNode transit) {
        TravelDTO.TransitRoute route = new TravelDTO.TransitRoute();

        // 耗时（秒转分钟）
        int seconds = transit.path("duration").asInt(0);
        route.setDuration(seconds / 60 + "分钟");

        // 距离（米转公里）
        int meters = transit.path("distance").asInt(0);
        route.setDistance(String.format("%.1f公里", meters / 1000.0));

        // 票价
        double cost = transit.path("cost").path("transit_fee").asDouble(0);
        route.setCost(cost > 0 ? cost + "元" : "免费");

        // 换乘步骤 + 折线坐标
        List<String> steps = new ArrayList<>();
        List<String> polylinePoints = new ArrayList<>();
        JsonNode segments = transit.path("segments");
        if (segments.isArray()) {
            for (JsonNode seg : segments) {
                // 步行段折线
                JsonNode walking = seg.path("walking");
                if (!walking.isMissingNode()) {
                    JsonNode wSteps = walking.path("steps");
                    if (wSteps.isArray()) {
                        for (JsonNode ws : wSteps) {
                            extractPolyline(ws.path("polyline").asText(""), polylinePoints);
                        }
                    }
                }
                // 公交折线
                JsonNode buslines = seg.path("bus").path("buslines");
                if (buslines.isArray() && buslines.size() > 0) {
                    extractPolyline(buslines.get(0).path("polyline").asText(""), polylinePoints);
                }
                // 地铁折线
                JsonNode railway = seg.path("railway");
                if (!railway.isMissingNode() && !railway.isMissingNode()) {
                    extractPolyline(railway.path("polyline").asText(""), polylinePoints);
                    // via_stops 折线补充
                    JsonNode viaStops = railway.path("via_stops");
                    if (!viaStops.isMissingNode() && viaStops.isArray()) {
                        for (JsonNode vs : viaStops) {
                            String loc = vs.path("location").asText("");
                            if (!isBlank(loc)) polylinePoints.add(loc);
                        }
                    }
                }
                String step = parseSegment(seg);
                if (step != null) steps.add(step);
            }
        }
        route.setSteps(steps);
        route.setPolyline(polylinePoints.isEmpty() ? null : polylinePoints);

        return route;
    }

    /**
     * 把高德折线字符串（"lng,lat;lng,lat;..."）解析为坐标点列表
     */
    private void extractPolyline(String raw, List<String> out) {
        if (isBlank(raw)) return;
        String[] parts = raw.split(";");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!isBlank(trimmed) && trimmed.contains(",")) {
                out.add(trimmed);
            }
        }
    }

    /**
     * 解析单段换乘步骤
     * 高德每个 segment 包含步行(walking) + 乘车(bus/railway) 两部分
     */
    private String parseSegment(JsonNode segment) {
        // 优先取地铁
        JsonNode railway = segment.path("railway");
        if (!railway.isMissingNode() && railway.size() > 0) {
            String lineName = text(railway, "name");
            String departStop = nestedText(railway, "departureStop", "name");
            if (isBlank(departStop)) {
                departStop = nestedText(railway, "departure_stop", "name");
            }
            String arrivalStop = nestedText(railway, "arrivalStop", "name");
            if (isBlank(arrivalStop)) {
                arrivalStop = nestedText(railway, "arrival_stop", "name");
            }
            int stops = railway.path("via_stops").isArray() ? railway.path("via_stops").size() + 1 : 0;

            // railway 字段有时是空对象，避免输出“乘，→，共1站”
            if (!isBlank(lineName) || !isBlank(departStop) || !isBlank(arrivalStop)) {
                return String.format("乘%s，%s→%s，共%d站",
                        fallback(lineName, "轨道交通"),
                        fallback(departStop, "起点站"),
                        fallback(arrivalStop, "终点站"),
                        stops > 0 ? stops : 1);
            }
        }

        // 再取公交
        JsonNode buslines = segment.path("bus").path("buslines");
        if (buslines.isArray() && buslines.size() > 0) {
            JsonNode bus = buslines.get(0);
            String lineName = text(bus, "name");
            String departStop = nestedText(bus, "departure_stop", "name");
            String arrivalStop = nestedText(bus, "arrival_stop", "name");
            int stops = bus.path("via_stops").isArray() ? bus.path("via_stops").size() + 1 : 0;
            return String.format("乘%s，%s→%s，共%d站",
                    fallback(lineName, "公交"),
                    fallback(departStop, "起点站"),
                    fallback(arrivalStop, "终点站"),
                    stops > 0 ? stops : 1);
        }

        // 步行段（只记录超过100米的）
        JsonNode walking = segment.path("walking");
        if (!walking.isMissingNode()) {
            int dist = walking.path("distance").asInt(0);
            if (dist > 100) return String.format("步行%d米", dist);
        }

        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String text(JsonNode node, String key) {
        if (node == null || node.isMissingNode()) return "";
        return node.path(key).asText("").trim();
    }

    private String nestedText(JsonNode node, String key, String nestedKey) {
        if (node == null || node.isMissingNode()) return "";
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) return "";
        return child.path(nestedKey).asText("").trim();
    }

    private String fallback(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static class GeocodeResult {
        private final String location;
        private final String reason;

        private GeocodeResult(String location, String reason) {
            this.location = location;
            this.reason = reason;
        }

        private static GeocodeResult success(String location) {
            return new GeocodeResult(location, "OK");
        }

        private static GeocodeResult fail(String reason) {
            return new GeocodeResult(null, reason);
        }
    }

    private static class RegeoResult {
        private final boolean success;
        private final String city;
        private final String origin;
        private final String reason;

        private RegeoResult(boolean success, String city, String origin, String reason) {
            this.success = success;
            this.city = city;
            this.origin = origin;
            this.reason = reason;
        }

        private static RegeoResult success(String city, String origin) {
            return new RegeoResult(true, city, origin, "OK");
        }

        private static RegeoResult fail(String reason) {
            return new RegeoResult(false, "", "", reason);
        }
    }
}
