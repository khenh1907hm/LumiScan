package com.example.Lumi.config;

import com.example.Lumi.model.User;
import com.example.Lumi.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) throws Exception {
        // Tạo admin mặc định nếu chưa có
        if (userService.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("admin123"); // Sẽ được mã hóa tự động
            admin.setFullName("Administrator");
            admin.setEmail("admin@lumiscan.com");
            admin.setRole(User.Role.ADMIN);
            
            userService.register(admin);
            System.out.println("✅ Admin user created: admin/admin123");
        }

        // Tạo employee mặc định nếu chưa có
        if (userService.findByUsername("employee").isEmpty()) {
            User employee = new User();
            employee.setUsername("employee");
            employee.setPassword("emp123"); // Sẽ được mã hóa tự động
            employee.setFullName("Employee");
            employee.setEmail("employee@lumiscan.com");
            employee.setRole(User.Role.EMPLOYEE);
            
            userService.register(employee);
            System.out.println("✅ Employee user created: employee/emp123");
        }
    }
}
