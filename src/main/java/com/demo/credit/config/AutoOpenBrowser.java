package com.demo.credit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

@Configuration
public class AutoOpenBrowser {

    private static final AtomicBoolean OPENED = new AtomicBoolean(false);

    @Value("${app.auto-open-browser:true}")
    private boolean autoOpen;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${app.auto-open-path:/borrower}")
    private String path;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!autoOpen)
            return;
        // Chỉ mở 1 lần cho mỗi process (DevTools restart sẽ không mở lại)
        if (!OPENED.compareAndSet(false, true))
            return;

        String url = "http://localhost:" + serverPort + (path.startsWith("/") ? path : "/" + path);
        try {
            // Ưu tiên Desktop API
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            // Fallback theo HĐH
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (Exception e) {
            // Không throw để tránh fail khởi động nếu máy không có GUI
            System.err.println("Auto-open browser failed: " + e);
        }
    }
}
