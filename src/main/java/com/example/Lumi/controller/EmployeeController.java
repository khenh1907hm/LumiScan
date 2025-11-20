package com.example.Lumi.controller;

import com.example.Lumi.model.TableEntity;
import com.example.Lumi.service.TableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/employee")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class EmployeeController {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeController.class);
    private final TableService tableService;

    public EmployeeController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/tables")
    public String manageTables(Model model) {
        logger.info("=== Starting employee tables page ===");
        try {
            // Fetch tables with error handling
            List<TableEntity> tables;
            try {
                tables = tableService.findAllTables();
                logger.debug("Tables fetched: {}", tables != null ? tables.size() : 0);
            } catch (Exception e) {
                logger.error("Error fetching tables: ", e);
                tables = new ArrayList<>();
            }

            // Filter null values
            if (tables != null) {
                tables = tables.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
                logger.debug("Tables after filter: {}", tables.size());
            } else {
                tables = new ArrayList<>();
            }

            model.addAttribute("tables", tables);

            // Count tables with error handling
            try {
                long totalTables = tableService.countAllTables();
                model.addAttribute("totalTables", totalTables);
                logger.debug("Total tables: {}", totalTables);
            } catch (Exception e) {
                logger.error("Error counting total tables: ", e);
                model.addAttribute("totalTables", 0);
            }

            try {
                long activeTables = tableService.countActiveTables();
                model.addAttribute("activeTables", activeTables);
                logger.debug("Active tables: {}", activeTables);
            } catch (Exception e) {
                logger.error("Error counting active tables: ", e);
                model.addAttribute("activeTables", 0);
            }

            logger.info("=== Successfully loaded employee tables page ===");
            return "employee/tables";

        } catch (Exception e) {
            logger.error("=== CRITICAL ERROR in manageTables ===", e);
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: {}", e.getCause().getMessage());
            }
            
            // Set safe default values
            model.addAttribute("tables", new ArrayList<>());
            model.addAttribute("totalTables", 0);
            model.addAttribute("activeTables", 0);
            model.addAttribute("error", "Có lỗi xảy ra khi tải dữ liệu: " + e.getMessage());
            
            return "employee/tables";
        }
    }
}