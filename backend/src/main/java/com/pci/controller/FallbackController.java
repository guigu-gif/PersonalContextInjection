package com.pci.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FallbackController {

    @RequestMapping(value = {
        "/", "/login", "/home", "/course", "/memo",
        "/notify", "/chat", "/settings", "/travel", "/guide"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
