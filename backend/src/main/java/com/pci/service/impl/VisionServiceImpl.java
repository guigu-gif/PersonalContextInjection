package com.pci.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pci.dto.CourseOpDTO;
import com.pci.entity.Course;
import com.pci.service.ISlotConfigService;
import com.pci.service.IVisionService;
import com.pci.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class VisionServiceImpl implements IVisionService {

    @SafeVarargs
    private static <T> List<T> listOf(T... items) {
        return Arrays.asList(items);
    }

    @Value("${vision.api-url}")
    private String apiUrl;

    @Value("${vision.api-key}")
    private String apiKey;

    @Value("${vision.model}")
    private String model;

    @Resource
    private ISlotConfigService slotConfigService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VisionServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(110000);
        this.restTemplate = new RestTemplate(factory);
    }

    private static final String SYSTEM_PROMPT =
        "你是一个课程表识别助手。仔细观察图片，以JSON格式返回识别结果，只输出JSON不要任何解释文字。\n\n" +
        "【节次时间】识别图片左侧标注的每小节课时间（每小节约45分钟），输出slots数组。\n" +
        "若图片标注的是大节（如第1大节=2小节），请拆分为小节，例如大节1(08:00-09:45)→slot1(08:00-08:45)+slot2(08:55-09:40)。\n" +
        "若图片无时间标注则输出空数组[]。\n\n" +
        "【课程信息】识别每个课程格子，输出courses数组。字段说明：\n" +
        "- weekday：周一=1 周二=2 周三=3 周四=4 周五=5 周六=6 周日=7\n" +
        "- startSlot/endSlot：对应slots中的小节编号\n" +
        "- weekStart/weekEnd：仔细查找格子内的周次标注。\n" +
        "  * 连续周次\"1-16周\"→weekStart=1,weekEnd=16\n" +
        "  * 不连续周次\"5-8,10-14周\"→拆成两条记录：第一条weekStart=5,weekEnd=8；第二条weekStart=10,weekEnd=14\n" +
        "  * 无周次标注→weekStart=1,weekEnd=16\n" +
        "- location：教室编号，如\"C3-105\"，去掉节次信息\"[01-02]节\"只保留教室号\n\n" +
        "输出格式（严格遵守）：\n" +
        "{\"slots\":[{\"slot\":1,\"start\":\"08:00\",\"end\":\"08:45\"},{\"slot\":2,\"start\":\"08:55\",\"end\":\"09:40\"},...],\n" +
        "\"courses\":[{\"name\":\"课程名\",\"teacher\":\"教师名或null\",\"location\":\"教室号或null\"," +
        "\"weekday\":1,\"startSlot\":1,\"endSlot\":2,\"weekStart\":1,\"weekEnd\":16}]}";

    @Override
    public CourseOpDTO.ParseResult recognizeSchedule(MultipartFile file) {
        CourseOpDTO.ParseResult result = new CourseOpDTO.ParseResult();
        result.setOps(new ArrayList<>());
        result.setUnresolved(new ArrayList<>());

        try {
            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            // 构造请求体
            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);

            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);

            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", "请识别这张课程表图片中的所有课程信息。");

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", listOf(imageContent, textContent));

            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", listOf(systemMsg, userMsg));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                apiUrl + "/chat/completions", entity, String.class);

            String content = extractContent(response.getBody());
            log.info("[Vision] raw content: {}", content);
            return parseAiResponse(content);

        } catch (Exception e) {
            result.getUnresolved().add("识别失败：" + e.getMessage());
            return result;
        }
    }

    private String extractContent(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    // 从模型返回中提取第一个合法的 JSON 对象（处理 ```json 包装和多余字符）
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

    private CourseOpDTO.ParseResult parseAiResponse(String content) {
        CourseOpDTO.ParseResult result = new CourseOpDTO.ParseResult();
        result.setOps(new ArrayList<>());
        result.setUnresolved(new ArrayList<>());

        try {
            // 找到最外层完整 JSON 对象（跳过 markdown 代码块和多余字符）
            String json = extractJson(content);
            if (json == null) {
                result.getUnresolved().add("AI返回格式异常，请重新拍摄或手动录入");
                return result;
            }
            JsonNode root = objectMapper.readTree(json);

            // 解析节次时间
            JsonNode slotsNode = root.path("slots");
            if (slotsNode.isArray() && slotsNode.size() > 0) {
                String slotsJson = objectMapper.writeValueAsString(slotsNode);
                Long userId = UserHolder.getUser().getId();
                slotConfigService.saveConfigForUser(userId, slotsJson);
                result.setSlotsUpdated(true);
            }

            // 解析课程
            JsonNode courses = root.path("courses");
            for (JsonNode node : courses) {
                Course c = new Course();
                c.setName(node.path("name").asText());
                c.setTeacher(nullIfEmpty(node.path("teacher").asText()));
                c.setLocation(nullIfEmpty(node.path("location").asText()));
                c.setWeekday(node.path("weekday").asInt());
                c.setStartSlot(node.path("startSlot").asInt());
                c.setEndSlot(node.path("endSlot").asInt());
                c.setWeekStart(node.has("weekStart") ? node.path("weekStart").asInt() : 1);
                c.setWeekEnd(node.has("weekEnd") ? node.path("weekEnd").asInt() : 16);
                c.setColor("#4f46e5");

                CourseOpDTO.Op op = new CourseOpDTO.Op();
                op.setAction("CREATE");
                op.setCourse(c);
                result.getOps().add(op);
            }

            if (result.getOps().isEmpty()) {
                result.getUnresolved().add("未识别到课程，请确认图片清晰度或手动录入");
            }
        } catch (Exception e) {
            result.getUnresolved().add("解析失败：" + e.getMessage());
        }
        return result;
    }

    private String nullIfEmpty(String s) {
        return (s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s)) ? null : s;
    }
}
