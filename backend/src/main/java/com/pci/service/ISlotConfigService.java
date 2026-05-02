package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.Result;
import com.pci.entity.SlotConfig;

public interface ISlotConfigService extends IService<SlotConfig> {
    Result getConfig();
    Result saveConfig(String slotsJson);
    void saveConfigForUser(Long userId, String slotsJson); // 供 VisionService 调用
}
