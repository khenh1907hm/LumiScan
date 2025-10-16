package com.example.Lumi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // 1️⃣ Cho phép truy cập ảnh QR từ thư mục static/qrcodes
        registry.addResourceHandler("/qrcodes/**")
                .addResourceLocations("classpath:/static/qrcodes/");

        // 2️⃣ Cho phép truy cập ảnh upload/menu trong thư mục uploads/menu ngoài project
        String uploadPath = System.getProperty("user.dir") + "/uploads/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);

    }
}