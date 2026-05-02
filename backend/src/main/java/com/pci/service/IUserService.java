package com.pci.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pci.dto.LoginFormDTO;
import com.pci.dto.Result;
import com.pci.entity.User;

public interface IUserService extends IService<User> {
    Result sendCode(String phone);
    Result login(LoginFormDTO loginForm);
}
