package com.example.libraryseat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 应用启动完成后在控制台打印接口文档地址（Knife4j），IDEA 运行窗口中可点击打开。
 */
@Component
public class SwaggerUrlPrinter {

    @Value("${server.port:8080}")
    private int serverPort;

    // 给启动阶段其它日志留出时间，让 Swagger 提示更“靠近输出底部”
    @Value("${swagger.url.print-delay-ms:800}")
    private long printDelayMs;

    @EventListener(ApplicationReadyEvent.class)
    public void printSwaggerUrl() {
        if (printDelayMs > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(printDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Knife4j 默认文档访问地址
        String url = "http://localhost:" + serverPort + "/doc.html";
        System.out.println();
        System.out.println("========== 接口文档（Knife4j） ==========");
        // 单独一行、无前导空格，便于 IDEA 识别为可点击链接
        System.out.println(url);
        System.out.println("========================================== (Ctrl+点击上面链接打开)");
        System.out.println();
    }
}
