package com.pci.controller;

import com.pci.dto.Result;
import com.pci.service.impl.ProfileServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/user/profile")
public class ProfileController {

    @Resource
    private ProfileServiceImpl profileService;

    @GetMapping
    public Result get() {
        return profileService.getProfile();
    }

    /** AI 或前端设置页调用，action + value 均在白名单内才执行 */
    @PutMapping("/action")
    public Result action(@RequestBody Map<String, String> body) {
        String action = body.get("action");
        String value  = body.get("value");
        if (action == null || value == null) {
            return Result.fail("参数不完整");
        }
        return profileService.applyAction(action, value);
    }
}
