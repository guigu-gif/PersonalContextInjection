package com.pci.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pci.entity.Course;
import com.pci.entity.Memo;
import com.pci.entity.Notification;
import com.pci.mapper.CourseMapper;
import com.pci.mapper.MemoMapper;
import com.pci.mapper.NotificationMapper;
import com.pci.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.Set;

@Component
public class RemindScheduler {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private MemoMapper memoMapper;
    @Resource
    private NotificationMapper notificationMapper;
    @Resource
    private CourseMapper courseMapper;
    @Resource
    private CourseRemindHelper courseRemindHelper;

    @Scheduled(fixedDelay = 60_000)
    public void processMemoReminders() {
        double now = Instant.now().toEpochMilli();
        Set<String> due = stringRedisTemplate.opsForZSet()
                .rangeByScore(RedisConstants.MEMO_REMIND_QUEUE, 0, now);
        if (due == null || due.isEmpty()) {
            return;
        }

        for (String idStr : due) {
            try {
                Long memoId = Long.parseLong(idStr);
                Memo memo = memoMapper.selectOne(
                        Wrappers.<Memo>lambdaQuery()
                                .eq(Memo::getId, memoId)
                                .eq(Memo::getReminded, 0)
                                .eq(Memo::getStatus, 0)
                );
                if (memo != null) {
                    Notification notification = new Notification();
                    notification.setUserId(memo.getUserId());
                    notification.setType(2);
                    notification.setRefId(memoId);
                    notification.setContent("备忘提醒：" + memo.getTitle());
                    notification.setIsRead(0);
                    notificationMapper.insert(notification);

                    memo.setReminded(1);
                    memoMapper.updateById(memo);
                }
            } catch (Exception ignored) {
            } finally {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.MEMO_REMIND_QUEUE, idStr);
            }
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void processCourseReminders() {
        double now = Instant.now().toEpochMilli();
        Set<String> due = stringRedisTemplate.opsForZSet()
                .rangeByScore(RedisConstants.COURSE_REMIND_QUEUE, 0, now);
        if (due == null || due.isEmpty()) return;

        for (String idStr : due) {
            stringRedisTemplate.opsForZSet().remove(RedisConstants.COURSE_REMIND_QUEUE, idStr);
            try {
                Long courseId = Long.parseLong(idStr);
                Course course = courseMapper.selectById(courseId);
                if (course == null) continue;

                Notification notification = new Notification();
                notification.setUserId(course.getUserId());
                notification.setType(1);
                notification.setRefId(courseId);
                notification.setContent("课程提醒：" + course.getName() + " 即将开始");
                notification.setIsRead(0);
                notificationMapper.insert(notification);

                courseRemindHelper.scheduleNext(course);
            } catch (Exception ignored) {
            }
        }
    }
}
