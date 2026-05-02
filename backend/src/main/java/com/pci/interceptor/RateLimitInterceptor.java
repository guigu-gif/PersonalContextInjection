package com.pci.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RateLimitInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY_PREFIX = "rate:limit:";

    private final StringRedisTemplate redisTemplate;
    private final List<LimitRule> rules = new ArrayList<LimitRule>();

    public RateLimitInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 登录验证码：1分钟最多5次
        rules.add(new LimitRule("/user/code", 60_000L, 5));
        // 登录：1分钟最多10次
        rules.add(new LimitRule("/user/login", 60_000L, 10));
        // 对话：1分钟最多30次
        rules.add(new LimitRule("/chat", 60_000L, 30));
        // 图片路由：1分钟最多12次
        rules.add(new LimitRule("/chat/image-route", 60_000L, 12));
        // AI解析：1分钟最多20次
        rules.add(new LimitRule("/memo/ai-parse", 60_000L, 20));
        rules.add(new LimitRule("/travel/ai-parse", 60_000L, 20));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        LimitRule rule = matchRule(uri);
        if (rule == null) {
            return true;
        }
        String identifier = resolveIdentifier(request);
        String key = KEY_PREFIX + uri + ":" + identifier;
        long now = System.currentTimeMillis();
        long windowStart = now - rule.windowMs;

        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
        Long count = redisTemplate.opsForZSet().zCard(key);
        if (count != null && count >= rule.maxRequests) {
            writeLimitResponse(response);
            return false;
        }
        redisTemplate.opsForZSet().add(key, now + "-" + System.nanoTime(), now);
        redisTemplate.expire(key, rule.windowMs + 5_000L, TimeUnit.MILLISECONDS);
        return true;
    }

    private LimitRule matchRule(String uri) {
        for (LimitRule rule : rules) {
            if (rule.path.equals(uri)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveIdentifier(HttpServletRequest request) {
        UserDTO user = UserHolder.getUser();
        if (user != null && user.getId() != null) {
            return "uid:" + user.getId();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.trim().isEmpty()) {
            String[] parts = xff.split(",");
            return "ip:" + parts[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void writeLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(Result.fail("请求过于频繁，请稍后再试")));
    }

    private static class LimitRule {
        private final String path;
        private final long windowMs;
        private final int maxRequests;

        private LimitRule(String path, long windowMs, int maxRequests) {
            this.path = path;
            this.windowMs = windowMs;
            this.maxRequests = maxRequests;
        }
    }
}
