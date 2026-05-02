package com.pci.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.UserPersona;
import com.pci.mapper.UserPersonaMapper;
import com.pci.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PersonaServiceImpl extends ServiceImpl<UserPersonaMapper, UserPersona> {

    private static final int MAX_FACTS = 20;
    // 允许的 factKey 白名单
    private static final List<String> ALLOWED_KEYS =
        java.util.Arrays.asList("身份", "偏好", "关注", "其他");

    public Result listPersona() {
        UserDTO user = UserHolder.getUser();
        List<UserPersona> list = lambdaQuery()
            .eq(UserPersona::getUserId, user.getId())
            .orderByDesc(UserPersona::getCreatedTime)
            .list();
        return Result.ok(list);
    }

    public Result addFact(Map<String, String> body) {
        UserDTO user = UserHolder.getUser();
        String key   = body.get("factKey");
        String value = body.get("factValue");
        String source = body.get("source");

        if (isBlank(key) || isBlank(value)) return Result.fail("参数不完整");
        if (!ALLOWED_KEYS.contains(key))    return Result.fail("不支持的事实类型");
        if (value.length() > 100)           return Result.fail("内容过长");
        if (!"manual".equals(source) && !"ai".equals(source)) source = "manual";

        // 同 key 已存在 → 覆盖（upsert）
        UserPersona existing = lambdaQuery()
            .eq(UserPersona::getUserId, user.getId())
            .eq(UserPersona::getFactKey, key)
            .one();
        if (existing != null) {
            if (existing.getFactValue().equals(value.trim())) return Result.ok(existing); // 完全相同直接返回
            existing.setFactValue(value.trim());
            existing.setSource(source);
            existing.setCreatedTime(LocalDateTime.now());
            updateById(existing);
            return Result.ok(existing);
        }

        long count = lambdaQuery().eq(UserPersona::getUserId, user.getId()).count();
        if (count >= MAX_FACTS) return Result.fail("画像条目已达上限（" + MAX_FACTS + "条），请先删除部分条目");

        UserPersona fact = new UserPersona();
        fact.setUserId(user.getId());
        fact.setFactKey(key);
        fact.setFactValue(value.trim());
        fact.setSource(source);
        fact.setCreatedTime(LocalDateTime.now());
        save(fact);
        return Result.ok(fact);
    }

    public Result deleteFact(Long id) {
        UserDTO user = UserHolder.getUser();
        UserPersona fact = lambdaQuery()
            .eq(UserPersona::getId, id)
            .eq(UserPersona::getUserId, user.getId())
            .one();
        if (fact == null) return Result.fail("条目不存在");
        removeById(id);
        return Result.ok();
    }

    public Result clearAll() {
        UserDTO user = UserHolder.getUser();
        remove(Wrappers.<UserPersona>lambdaQuery().eq(UserPersona::getUserId, user.getId()));
        return Result.ok();
    }

    /** 供 ChatServiceImpl 调用，每个 key 最多取2条，保证四类都有代表 */
    public List<UserPersona> getTopFacts(Long userId) {
        List<UserPersona> all = lambdaQuery()
            .eq(UserPersona::getUserId, userId)
            .orderByDesc(UserPersona::getCreatedTime)
            .list();
        // 每个 key 最多保留2条
        java.util.Map<String, Integer> counter = new java.util.HashMap<>();
        List<UserPersona> result = new java.util.ArrayList<>();
        for (UserPersona f : all) {
            int cnt = counter.getOrDefault(f.getFactKey(), 0);
            if (cnt < 2) {
                result.add(f);
                counter.put(f.getFactKey(), cnt + 1);
            }
        }
        return result;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
