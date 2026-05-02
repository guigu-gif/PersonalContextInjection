package com.pci.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pci.dto.Result;
import com.pci.entity.Announcement;
import com.pci.mapper.AnnouncementMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/announcement")
public class AnnouncementController {

    @Resource
    private AnnouncementMapper announcementMapper;

    @GetMapping
    public Result list() {
        List<Announcement> list = announcementMapper.selectList(
            Wrappers.<Announcement>lambdaQuery()
                .orderByDesc(Announcement::getId)
                .last("LIMIT 5")
        );
        return Result.ok(list);
    }
}
