package com.pci.controller;

import com.pci.dto.Result;
import com.pci.service.ISlotConfigService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/slot-config")
public class SlotConfigController {

    @Resource
    private ISlotConfigService slotConfigService;

    @GetMapping
    public Result get() { return slotConfigService.getConfig(); }

    @PutMapping
    public Result save(@RequestBody Map<String, String> body) {
        return slotConfigService.saveConfig(body.get("slotsJson"));
    }
}
