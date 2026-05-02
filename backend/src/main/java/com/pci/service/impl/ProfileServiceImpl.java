package com.pci.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.ProfileLog;
import com.pci.entity.UserProfile;
import com.pci.mapper.ProfileLogMapper;
import com.pci.mapper.UserProfileMapper;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> {

    // 白名单：操作 → 合法值列表
    private static final Map<String, List<String>> ALLOWED = new HashMap<>();
    static {
        ALLOWED.put("SET_IDENTITY",  Arrays.asList("student", "elder", "general"));
        ALLOWED.put("SET_FONT_SIZE", Arrays.asList("normal", "large", "xlarge"));
        ALLOWED.put("SET_THEME",     Arrays.asList("default", "elder", "dark"));
        ALLOWED.put("SET_WALLPAPER", Arrays.asList("none", "campus", "nature", "minimal"));
    }

    @Resource
    private ProfileLogMapper profileLogMapper;

    public Result getProfile() {
        UserDTO user = UserHolder.getUser();
        UserProfile profile = getOrCreate(user.getId());
        return Result.ok(profile);
    }

    public Result applyAction(String action, String value) {
        if (!ALLOWED.containsKey(action)) {
            return Result.fail("不支持的操作类型");
        }
        if (!ALLOWED.get(action).contains(value)) {
            return Result.fail("非法参数值");
        }

        UserDTO user = UserHolder.getUser();
        UserProfile profile = getOrCreate(user.getId());
        String oldValue = getField(profile, action);

        setField(profile, action, value);
        saveOrUpdate(profile);

        ProfileLog log = new ProfileLog();
        log.setUserId(user.getId());
        log.setAction(action);
        log.setOldValue(oldValue);
        log.setNewValue(value);
        log.setCreatedTime(LocalDateTime.now());
        profileLogMapper.insert(log);

        return Result.ok(profile);
    }

    private UserProfile getOrCreate(Long userId) {
        UserProfile profile = getById(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setIdentity("general");
            profile.setFontSize("normal");
            profile.setTheme("default");
            profile.setWallpaper("none");
            save(profile);
        }
        return profile;
    }

    private String getField(UserProfile p, String action) {
        switch (action) {
            case "SET_IDENTITY":  return p.getIdentity();
            case "SET_FONT_SIZE": return p.getFontSize();
            case "SET_THEME":     return p.getTheme();
            case "SET_WALLPAPER": return p.getWallpaper();
            default: return null;
        }
    }

    private void setField(UserProfile p, String action, String value) {
        switch (action) {
            case "SET_IDENTITY":  p.setIdentity(value); break;
            case "SET_FONT_SIZE": p.setFontSize(value); break;
            case "SET_THEME":     p.setTheme(value); break;
            case "SET_WALLPAPER": p.setWallpaper(value); break;
        }
    }
}
