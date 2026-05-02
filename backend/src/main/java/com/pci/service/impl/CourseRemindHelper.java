package com.pci.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pci.entity.Course;
import com.pci.entity.SlotConfig;
import com.pci.mapper.SlotConfigMapper;
import com.pci.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Component
public class CourseRemindHelper {

    private static final int REMIND_MINUTES_BEFORE = 15;

    private static final String DEFAULT_SLOTS =
        "[{\"slot\":1,\"start\":\"08:00\"},{\"slot\":2,\"start\":\"08:55\"}," +
        "{\"slot\":3,\"start\":\"10:00\"},{\"slot\":4,\"start\":\"10:55\"}," +
        "{\"slot\":5,\"start\":\"14:00\"},{\"slot\":6,\"start\":\"14:55\"}," +
        "{\"slot\":7,\"start\":\"16:00\"},{\"slot\":8,\"start\":\"16:55\"}," +
        "{\"slot\":9,\"start\":\"19:00\"},{\"slot\":10,\"start\":\"19:55\"}," +
        "{\"slot\":11,\"start\":\"20:50\"},{\"slot\":12,\"start\":\"21:45\"}]";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SlotConfigMapper slotConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void scheduleNext(Course course) {
        try {
            LocalTime slotStart = resolveSlotStart(course.getUserId(), course.getStartSlot());
            LocalDate nextDate = nextWeekday(LocalDate.now(), course.getWeekday());
            LocalDateTime remindAt = nextDate.atTime(slotStart).minusMinutes(REMIND_MINUTES_BEFORE);
            long score = remindAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(
                RedisConstants.COURSE_REMIND_QUEUE,
                String.valueOf(course.getId()),
                score
            );
        } catch (Exception ignored) {}
    }

    public void cancel(Long courseId) {
        stringRedisTemplate.opsForZSet().remove(
            RedisConstants.COURSE_REMIND_QUEUE,
            String.valueOf(courseId)
        );
    }

    private LocalDate nextWeekday(LocalDate from, int weekday) {
        // weekday: 1=Monday ... 7=Sunday, matches DayOfWeek.getValue()
        DayOfWeek target = DayOfWeek.of(weekday);
        LocalDate date = from;
        // start from tomorrow to avoid re-triggering today's already-passed class
        date = date.plusDays(1);
        while (date.getDayOfWeek() != target) {
            date = date.plusDays(1);
        }
        return date;
    }

    private LocalTime resolveSlotStart(Long userId, int slotNum) throws Exception {
        SlotConfig config = slotConfigMapper.selectOne(
            Wrappers.<SlotConfig>lambdaQuery().eq(SlotConfig::getUserId, userId)
        );
        String json = config != null ? config.getSlotsJson() : DEFAULT_SLOTS;
        JsonNode arr = objectMapper.readTree(json);
        for (JsonNode node : arr) {
            if (node.get("slot").asInt() == slotNum) {
                return LocalTime.parse(node.get("start").asText());
            }
        }
        return LocalTime.of(8, 0);
    }
}
