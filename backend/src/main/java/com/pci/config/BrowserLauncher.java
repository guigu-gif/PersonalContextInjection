package com.pci.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.awt.Desktop;

@Slf4j
@Component
public class BrowserLauncher {

    @Value("${pci.browser.open:false}")
    private boolean openBrowser;

    @Value("${pci.browser.url:http://localhost:5173/login}")
    private String browserUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!openBrowser) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(browserUrl));
                log.info("已自动打开浏览器: {}", browserUrl);
            } else {
                Runtime.getRuntime().exec("cmd /c start " + browserUrl);
            }
        } catch (Exception e) {
            log.warn("自动打开浏览器失败: {}", e.getMessage());
        }
    }
}
