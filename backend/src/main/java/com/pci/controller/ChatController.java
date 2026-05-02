package com.pci.controller;

import com.pci.dto.ChatDTO;
import com.pci.dto.ChatImageDTO;
import com.pci.dto.Result;
import com.pci.service.IChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private IChatService chatService;

    @PostMapping
    public Result chat(@Valid @RequestBody ChatDTO.ChatRequest request) {
        return chatService.chat(request.getMessage().trim(), request.getHistory());
    }

    @GetMapping("/context")
    public Result context() {
        return chatService.buildContextSummary();
    }

    @PostMapping("/image-route")
    public Result imageRoute(@Valid @RequestBody ChatImageDTO.ImageRouteRequest request) {
        return chatService.imageRoute(request);
    }
}
