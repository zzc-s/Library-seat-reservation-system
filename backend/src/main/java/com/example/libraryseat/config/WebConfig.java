package com.example.libraryseat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置头像上传文件的访问路径
        // 尝试多种路径解析方式，确保能找到uploads目录
        
        String projectRoot = System.getProperty("user.dir");
        
        // 方式1: 如果从backend目录启动，uploads应该在项目根目录（父目录）
        Path uploadsPath1 = null;
        if (projectRoot.endsWith("backend")) {
            uploadsPath1 = Paths.get(projectRoot).getParent().resolve("uploads").toAbsolutePath();
        } else {
            uploadsPath1 = Paths.get(projectRoot, "uploads").toAbsolutePath();
        }
        
        // 方式2: 项目根目录下的uploads（如果从项目根目录启动）
        Path uploadsPath2 = Paths.get(projectRoot, "uploads").toAbsolutePath();
        
        // 方式3: backend目录下的uploads（如果从backend目录启动）
        Path uploadsPath3 = Paths.get(projectRoot, "uploads").toAbsolutePath();
        
        // 方式4: 如果从项目根目录启动，尝试backend/uploads
        Path uploadsPath4 = Paths.get(projectRoot, "backend", "uploads").toAbsolutePath();
        
        // 选择存在的路径，优先级：项目根目录/uploads > backend/uploads
        Path uploadsPath = uploadsPath1;
        File uploadsDir1 = uploadsPath1.toFile();
        File uploadsDir2 = uploadsPath2.toFile();
        File uploadsDir3 = uploadsPath3.toFile();
        File uploadsDir4 = uploadsPath4.toFile();
        
        // 优先选择项目根目录下的uploads（因为用户的图片在那里）
        if (uploadsDir1.exists() && uploadsDir1.isDirectory()) {
            uploadsPath = uploadsPath1; // 项目根目录/uploads
        } else if (uploadsDir2.exists() && uploadsDir2.isDirectory()) {
            uploadsPath = uploadsPath2; // 项目根目录/uploads
        } else if (uploadsDir4.exists() && uploadsDir4.isDirectory()) {
            uploadsPath = uploadsPath4; // backend/uploads
        } else if (uploadsDir3.exists() && uploadsDir3.isDirectory()) {
            uploadsPath = uploadsPath3; // 当前目录下的uploads
        } else {
            // 如果都不存在，使用项目根目录下的uploads
            if (projectRoot.endsWith("backend")) {
                uploadsPath = Paths.get(projectRoot).getParent().resolve("uploads").toAbsolutePath();
            } else {
                uploadsPath = uploadsPath2;
            }
            uploadsPath.toFile().mkdirs();
            // 创建book-covers子目录
            Path bookCoversPath = uploadsPath.resolve("book-covers");
            bookCoversPath.toFile().mkdirs();
        }
        
        // 确保路径格式正确（Windows路径需要特殊处理）
        String uploadsLocation = "file:" + uploadsPath.toString().replace("\\", "/") + "/";
        if (!uploadsLocation.endsWith("/")) {
            uploadsLocation += "/";
        }
        
        System.out.println("========================================");
        System.out.println("配置静态资源路径: /uploads/**");
        System.out.println("映射到: " + uploadsLocation);
        System.out.println("路径是否存在: " + uploadsPath.toFile().exists());
        System.out.println("当前工作目录: " + projectRoot);
        System.out.println("book-covers目录: " + uploadsPath.resolve("book-covers").toFile().exists());
        System.out.println("========================================");
        
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsLocation)
                .setCachePeriod(3600); // 缓存1小时
    }
}
