package com.pci.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pci.dto.ChatImageDTO;
import com.pci.dto.GuideDTO;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.Course;
import com.pci.entity.Memo;
import com.pci.entity.Notification;
import com.pci.mapper.CourseMapper;
import com.pci.mapper.MemoMapper;
import com.pci.mapper.NotificationMapper;
import com.pci.service.IChatService;
import com.pci.service.IGuideService;
import com.pci.service.ISemesterService;
import com.pci.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class ChatServiceImpl implements IChatService {

    @Value("${zhipu.chat-url}")
    private String chatUrl;

    @Value("${zhipu.api-key}")
    private String apiKey;

    @Value("${zhipu.chat-model}")
    private String chatModel;

    @Value("${vision.api-url:}")
    private String visionApiUrl;

    @Value("${vision.model:}")
    private String visionModel;

    @Value("${vision.api-key:}")
    private String visionApiKey;

    @Resource
    private CourseMapper courseMapper;
    @Resource
    private MemoMapper memoMapper;
    @Resource
    private NotificationMapper notificationMapper;
    @Resource
    private ISemesterService semesterService;

    @Resource
    private PersonaServiceImpl personaService;
    @Resource
    private IGuideService guideService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public Result chat(String message, List<Map<String, String>> history) {
        UserDTO user = UserHolder.getUser();
        try {
            String city = extractCity(message);
            List<GuideDTO.AiEvidence> citations = guideService.searchForAi(city, message, 3);
            String systemPrompt = buildSystemPrompt(user.getId(), citations);
            List<Map<String, String>> messages = new ArrayList<>();

            Map<String, String> sys = new HashMap<>();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            messages.add(sys);

            if (history != null) {
                for (Map<String, String> h : history) {
                    if ("user".equals(h.get("role")) || "assistant".equals(h.get("role"))) {
                        messages.add(h);
                    }
                }
            }

            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", message);
            messages.add(userMsg);

            Map<String, Object> body = new HashMap<>();
            body.put("model", chatModel);
            body.put("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(chatUrl, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String reply = root.path("choices").get(0).path("message").path("content").asText();
            GuideDTO.ChatAnswer answer = new GuideDTO.ChatAnswer();
            answer.setReply(reply);
            answer.setCitations(citations);
            return Result.ok(answer);

        } catch (Exception e) {
            log.error("[Chat] error", e);
            return Result.fail("AI 暂时无法响应，请稍后重试");
        }
    }

    @Override
    public Result buildContextSummary() {
        UserDTO user = UserHolder.getUser();
        Map<String, Object> summary = new HashMap<>();
        summary.put("todayCourses", getTodayCourses(user.getId()));
        summary.put("pendingMemos", getPendingMemos(user.getId()));
        summary.put("unreadCount", getUnreadCount(user.getId()));
        summary.put("date", LocalDate.now().toString());
        summary.put("weekday", weekdayLabel(LocalDate.now().getDayOfWeek()));
        return Result.ok(summary);
    }

    @Override
    public Result imageRoute(ChatImageDTO.ImageRouteRequest request) {
        try {
            String instruction = request.getInstruction() == null ? "" : request.getInstruction().trim();
            String base64 = request.getImageBase64() == null ? "" : request.getImageBase64().trim();
            if (base64.isEmpty()) {
                return Result.fail("图片不能为空");
            }
            String mimeType = request.getImageMimeType();
            if (mimeType == null || mimeType.trim().isEmpty()) {
                mimeType = "image/jpeg";
            }

            String systemPrompt =
                "你是多模块助手路由器。请根据图片和用户补充文字，判断应跳转到哪个功能页面。\n" +
                "可选module：travel,schedule,memo,notify,guide,settings,none。\n" +
                "规则：\n" +
                "1) 只有当模块明确时 shouldJump=true，否则 shouldJump=false,module=none。\n" +
                "2) 如果是travel，尽量提取 draft.city,draft.origin,draft.destination。\n" +
                "3) 只输出JSON，不要解释文字。\n" +
                "JSON格式：\n" +
                "{\"module\":\"travel|schedule|memo|notify|guide|settings|none\",\"shouldJump\":true|false," +
                "\"reason\":\"简短原因\",\"confidence\":0.0," +
                "\"draft\":{\"city\":\"\",\"origin\":\"\",\"destination\":\"\"}}";

            Map<String, Object> imageUrl = new HashMap<>();
            imageUrl.put("url", "data:" + mimeType + ";base64," + base64);

            Map<String, Object> imageContent = new HashMap<>();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);

            Map<String, Object> textContent = new HashMap<>();
            textContent.put("type", "text");
            textContent.put("text", instruction.isEmpty() ? "请判断图片对应功能模块并返回路由结果" : instruction);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", Arrays.asList(imageContent, textContent));

            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", visionModel);
            body.put("messages", Arrays.asList(systemMsg, userMsg));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(visionApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                visionApiUrl + "/chat/completions", entity, String.class);

            String content = objectMapper.readTree(response.getBody())
                .path("choices").get(0).path("message").path("content").asText("");

            String json = extractJson(content);
            if (json == null) {
                return Result.fail("图片解析失败：模型未返回可解析结果");
            }

            JsonNode root = objectMapper.readTree(json);
            ChatImageDTO.ImageRouteResult result = new ChatImageDTO.ImageRouteResult();
            String module = root.path("module").asText("none").toLowerCase(Locale.ROOT);
            Set<String> allowed = new HashSet<>(Arrays.asList(
                "travel", "schedule", "memo", "notify", "guide", "settings", "none"
            ));
            if (!allowed.contains(module)) {
                module = "none";
            }
            result.setModule(module);
            result.setShouldJump(root.path("shouldJump").asBoolean(false) && !"none".equals(module));
            result.setReason(root.path("reason").asText("未识别到明确模块"));
            result.setConfidence(root.path("confidence").asDouble(0.0));

            Map<String, String> draft = new HashMap<>();
            JsonNode draftNode = root.path("draft");
            if (draftNode.isObject()) {
                draft.put("city", draftNode.path("city").asText(""));
                draft.put("origin", draftNode.path("origin").asText(""));
                draft.put("destination", draftNode.path("destination").asText(""));
            }
            result.setDraft(draft);

            return Result.ok(result);
        } catch (Exception e) {
            log.error("[Chat] imageRoute error", e);
            return Result.fail("图片解析失败，请稍后重试");
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isEmpty()) return null;
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    return content.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private String buildSystemPrompt(Long userId, List<GuideDTO.AiEvidence> citations) {
        StringBuilder sb = new StringBuilder();
        LocalDate today = LocalDate.now();
        sb.append("你是用户的个人智能助手，了解用户的课程安排和待办事项，能够回答跨功能的问题。\n");
        sb.append("今天是 ").append(today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")))
          .append("，").append(weekdayLabel(today.getDayOfWeek())).append("。\n\n");

        List<Course> courses = getTodayCourses(userId);
        if (!courses.isEmpty()) {
            sb.append("【今日课程】\n");
            for (Course c : courses) {
                sb.append("- ").append(c.getName())
                  .append("，第").append(c.getStartSlot()).append("-").append(c.getEndSlot()).append("节");
                if (c.getLocation() != null) sb.append("，").append(c.getLocation());
                if (c.getTeacher() != null) sb.append("，").append(c.getTeacher());
                sb.append("\n");
            }
        } else {
            sb.append("【今日课程】今天没有课程安排。\n");
        }

        sb.append("\n");
        List<Memo> memos = getPendingMemos(userId);
        if (!memos.isEmpty()) {
            sb.append("【近期待办备忘录】\n");
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM月dd日 HH:mm");
            for (Memo m : memos) {
                sb.append("- ").append(m.getTitle());
                if (m.getRemindTime() != null) {
                    sb.append("（提醒：").append(m.getRemindTime().format(fmt)).append("）");
                }
                sb.append("\n");
            }
        } else {
            sb.append("【近期待办备忘录】暂无待办事项。\n");
        }

        long unread = getUnreadCount(userId);
        sb.append("\n【未读通知】").append(unread).append(" 条。\n");

        // 注入用户画像
        List<com.pci.entity.UserPersona> facts = personaService.getTopFacts(userId);
        if (!facts.isEmpty()) {
            sb.append("\n【用户画像】\n");
            for (com.pci.entity.UserPersona f : facts) {
                sb.append("- ").append(f.getFactKey()).append("：").append(f.getFactValue()).append("\n");
            }
        }

        if (citations != null && !citations.isEmpty()) {
            sb.append("\n【可用攻略证据】\n");
            for (GuideDTO.AiEvidence c : citations) {
                sb.append("- [来源ID=").append(c.getGuideId()).append("]")
                        .append("[").append(c.getSourceType()).append("]")
                        .append("[评分=").append(c.getScore()).append("] ")
                        .append(c.getTitle()).append("：")
                        .append(c.getSnippet()).append("\n");
            }
        } else {
            sb.append("\n【可用攻略证据】暂无匹配攻略。\n");
        }

        sb.append("\n回答规则：\n");
        sb.append("1) 先给结论，再给步骤。\n");
        sb.append("2) 涉及攻略建议时，必须引用来源ID（格式：来源ID=xx）。\n");
        sb.append("3) 区分“路线事实（高可信）”和“经验建议（中可信）”。\n");
        sb.append("4) 回答简洁自然，不要重复整段上下文。\n");
        return sb.toString();
    }

    private String extractCity(String message) {
        if (message == null) return "上海";
        String text = message.trim();
        if (text.contains("上海")) return "上海";
        if (text.contains("杭州")) return "杭州";
        if (text.contains("南京")) return "南京";
        return "上海";
    }

    private List<Course> getTodayCourses(Long userId) {
        int weekday = localDayToWeekday(LocalDate.now().getDayOfWeek());
        // 查当前学期当前周的今日课程
        Result semResult = semesterService.currentWeek();
        int currentWeek = 1;
        if (Boolean.TRUE.equals(semResult.getSuccess()) && semResult.getData() instanceof Map) {
            Object w = ((Map<?, ?>) semResult.getData()).get("week");
            if (w instanceof Number) currentWeek = ((Number) w).intValue();
        }
        final int week = currentWeek;
        return courseMapper.selectList(
            Wrappers.<Course>lambdaQuery()
                .eq(Course::getUserId, userId)
                .eq(Course::getWeekday, weekday)
                .le(Course::getWeekStart, week)
                .ge(Course::getWeekEnd, week)
                .orderByAsc(Course::getStartSlot)
        );
    }

    private List<Memo> getPendingMemos(Long userId) {
        return memoMapper.selectList(
            Wrappers.<Memo>lambdaQuery()
                .eq(Memo::getUserId, userId)
                .eq(Memo::getStatus, 0)
                .orderByAsc(Memo::getRemindTime)
                .last("limit 5")
        );
    }

    private long getUnreadCount(Long userId) {
        return notificationMapper.selectCount(
            Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
        );
    }

    private int localDayToWeekday(DayOfWeek dow) {
        return dow == DayOfWeek.SUNDAY ? 7 : dow.getValue();
    }

    private String weekdayLabel(DayOfWeek dow) {
        String[] labels = {"周一","周二","周三","周四","周五","周六","周日"};
        return labels[dow.getValue() - 1];
    }
}
