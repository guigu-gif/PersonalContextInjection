package com.pci.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BrowserLauncher {

    @Value("${pci.browser.open:false}")
    private boolean openBrowser;

    @Value("${pci.browser.url:http://localhost:8082/login}")
    private String browserUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!openBrowser) return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                // Windows: start 命令需要空字符串作为窗口标题，否则 URL 中的 :// 会被误解析
                Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", browserUrl});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", browserUrl});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", browserUrl});
            }
            log.info("已自动打开浏览器: {}", browserUrl);
        } catch (Exception e) {
            log.warn("自动打开浏览器失败: {}", e.getMessage());
        }
    }
}
