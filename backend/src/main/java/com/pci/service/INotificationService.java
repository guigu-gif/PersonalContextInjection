package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.Result;
import com.pci.entity.Notification;

public interface INotificationService extends IService<Notification> {
    Result listNotifications(Integer page, Integer size);

    Result unreadSummary();

    Result markRead(Long id);
}
