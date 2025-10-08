package com.example.Lumi.config;

import com.example.Lumi.model.User;
import com.example.Lumi.model.TableEntity;
import com.example.Lumi.service.UserService;
import com.example.Lumi.service.TableService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final TableService tableService;

    public DataInitializer(UserService userService, TableService tableService) {
        this.userService = userService;
        this.tableService = tableService;
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

        // Create sample tables if none exist
        if (tableService.countAllTables() == 0) {
            for (int i = 1; i <= 5; i++) {
                TableEntity table = new TableEntity();
                table.setTableNumber("T" + i);
                table.setStatus("available");
                try {
                    tableService.createTable(table);
                    System.out.println("✅ Created table: " + table.getTableNumber());
                } catch (Exception e) {
                    System.err.println("❌ Error creating table " + table.getTableNumber() + ": " + e.getMessage());
                }
            }
        }
    }
}
