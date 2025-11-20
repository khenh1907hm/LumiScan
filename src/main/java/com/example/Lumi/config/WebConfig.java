package com.example.Lumi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 1️⃣ Cho phép truy cập ảnh QR từ thư mục static/qrcodes
        registry.addResourceHandler("/qrcodes/**")
                .addResourceLocations("classpath:/static/qrcodes/");

        // 2️⃣ Cho phép truy cập ảnh upload trong thư mục uploads ngoài project
        String uploadPath = System.getProperty("user.dir") + "/uploads/";
        System.out.println("=== WebConfig: Upload path = " + uploadPath + " ===");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath)
                .setCachePeriod(3600); // Cache 1 hour

    }
}