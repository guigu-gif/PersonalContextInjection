package com.pci.controller;

import com.pci.dto.Result;
import com.pci.service.INotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/notify")
public class NotificationController {

    @Resource
    private INotificationService notificationService;

    @GetMapping
    public Result list(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer size) {
        return notificationService.listNotifications(page, size);
    }

    @GetMapping("/summary")
    public Result summary() {
        return notificationService.unreadSummary();
    }

    @PutMapping("/{id}/read")
    public Result markRead(@PathVariable Long id) {
        return notificationService.markRead(id);
    }
}
