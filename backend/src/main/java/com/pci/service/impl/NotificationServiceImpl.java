package com.pci.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.Notification;
import com.pci.mapper.NotificationMapper;
import com.pci.service.INotificationService;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    @Override
    public Result listNotifications(Integer page, Integer size) {
        UserDTO user = UserHolder.getUser();
        int pageNo = page == null || page < 1 ? 1 : page;
        int pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        int offset = (pageNo - 1) * pageSize;

        LambdaQueryWrapper<Notification> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Notification::getUserId, user.getId())
                .orderByDesc(Notification::getCreatedTime)
                .last("limit " + offset + "," + pageSize);

        List<Notification> list = list(wrapper);
        long total = count(Wrappers.<Notification>lambdaQuery().eq(Notification::getUserId, user.getId()));
        return Result.ok(list, total);
    }

    @Override
    public Result unreadSummary() {
        UserDTO user = UserHolder.getUser();
        long unread = count(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, user.getId())
                .eq(Notification::getIsRead, 0));

        List<Notification> latest = list(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, user.getId())
                .orderByDesc(Notification::getCreatedTime)
                .last("limit 3"));

        Map<String, Object> data = new HashMap<>();
        data.put("unread", unread);
        data.put("latest", latest);
        return Result.ok(data);
    }

    @Override
    public Result markRead(Long id) {
        UserDTO user = UserHolder.getUser();
        Notification notification = lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, user.getId())
                .one();
        if (notification == null) {
            return Result.fail("通知不存在");
        }
        notification.setIsRead(1);
        updateById(notification);
        return Result.ok();
    }
}
