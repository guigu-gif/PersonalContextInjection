package com.pci.controller;

import cn.hutool.core.bean.BeanUtil;
import com.pci.dto.LoginFormDTO;
import com.pci.dto.Result;
import com.pci.dto.UserDTO;
import com.pci.entity.User;
import com.pci.service.IUserService;
import com.pci.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;

@Slf4j
@Validated
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone")
                           @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String phone) {
        return userService.sendCode(phone);
    }

    @PostMapping("/login")
    public Result login(@Valid @RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }

    @PostMapping("/logout")
    public Result logout() {
        UserHolder.removeUser();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me() {
        return Result.ok(UserHolder.getUser());
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) return Result.ok();
        return Result.ok(BeanUtil.copyProperties(user, UserDTO.class));
    }
}
