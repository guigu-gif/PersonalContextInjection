package com.pci.controller;

import com.pci.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/forum")
public class ForumController {

    @GetMapping
    public Result list() {
        return Result.fail("论坛功能即将上线");
    }
}
