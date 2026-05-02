package com.pci.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BrowserLauncher {

    @Value("${pci.browser.url:http://localhost:8082/login}")
    private String browserUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("========================================");
        log.info("  PCI 启动成功，访问地址：");
        log.info("  {}", browserUrl);
        log.info("  默认账号：admin / admin123");
        log.info("========================================");
    }
}
