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
        log.info("  PCI 启动成功：{}", browserUrl);
        log.info("  默认账号：13800000000 / admin123");
        log.info("========================================");
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", "", browserUrl);
                } else if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", browserUrl);
                } else {
                    pb = new ProcessBuilder("xdg-open", browserUrl);
                }
                pb.start();
            } catch (Exception e) {
                log.warn("自动打开浏览器失败: {}", e.getMessage());
            }
        }, "browser-launcher").start();
    }
}
