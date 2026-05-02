package com.pci.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.SlotConfig;
import com.pci.mapper.SlotConfigMapper;
import com.pci.service.ISlotConfigService;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;

@Service
public class SlotConfigServiceImpl extends ServiceImpl<SlotConfigMapper, SlotConfig> implements ISlotConfigService {

    private static final String DEFAULT_SLOTS =
        "[{\"slot\":1,\"start\":\"08:00\",\"end\":\"08:45\"}," +
        "{\"slot\":2,\"start\":\"08:55\",\"end\":\"09:40\"}," +
        "{\"slot\":3,\"start\":\"10:00\",\"end\":\"10:45\"}," +
        "{\"slot\":4,\"start\":\"10:55\",\"end\":\"11:40\"}," +
        "{\"slot\":5,\"start\":\"14:00\",\"end\":\"14:45\"}," +
        "{\"slot\":6,\"start\":\"14:55\",\"end\":\"15:40\"}," +
        "{\"slot\":7,\"start\":\"16:00\",\"end\":\"16:45\"}," +
        "{\"slot\":8,\"start\":\"16:55\",\"end\":\"17:40\"}," +
        "{\"slot\":9,\"start\":\"19:00\",\"end\":\"19:45\"}," +
        "{\"slot\":10,\"start\":\"19:55\",\"end\":\"20:40\"}," +
        "{\"slot\":11,\"start\":\"20:50\",\"end\":\"21:35\"}," +
        "{\"slot\":12,\"start\":\"21:45\",\"end\":\"22:30\"}]";

    @Override
    public Result getConfig() {
        UserDTO user = UserHolder.getUser();
        SlotConfig config = lambdaQuery().eq(SlotConfig::getUserId, user.getId()).one();
        if (config == null) {
            // 返回默认配置，不写库
            return Result.ok(DEFAULT_SLOTS);
        }
        return Result.ok(config.getSlotsJson());
    }

    @Override
    public Result saveConfig(String slotsJson) {
        UserDTO user = UserHolder.getUser();
        SlotConfig existing = lambdaQuery().eq(SlotConfig::getUserId, user.getId()).one();
        if (existing == null) {
            SlotConfig config = new SlotConfig();
            config.setUserId(user.getId());
            config.setSlotsJson(slotsJson);
            save(config);
        } else {
            existing.setSlotsJson(slotsJson);
            updateById(existing);
        }
        return Result.ok();
    }

    @Override
    public void saveConfigForUser(Long userId, String slotsJson) {
        SlotConfig existing = lambdaQuery().eq(SlotConfig::getUserId, userId).one();
        if (existing == null) {
            SlotConfig config = new SlotConfig();
            config.setUserId(userId);
            config.setSlotsJson(slotsJson);
            save(config);
        } else {
            existing.setSlotsJson(slotsJson);
            updateById(existing);
        }
    }
}
