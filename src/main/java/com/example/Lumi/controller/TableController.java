package com.example.Lumi.controller;

import com.example.Lumi.model.TableEntity;
import com.example.Lumi.service.TableService;
import com.google.zxing.WriterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/admin/tables")
public class TableController {
    
    private static final Logger logger = LoggerFactory.getLogger(TableController.class);
    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String listTables(Model model, Authentication authentication) {
        logger.info("=== Starting listTables method ===");
        try {
            // Log authentication details
            logger.info("User: {}, Roles: {}", 
                authentication.getName(), 
                authentication.getAuthorities());

            // Fetch tables
            List<TableEntity> tables = tableService.findAllTables();
            logger.info("Found {} tables", tables.size());
            if (tables.isEmpty()) {
                logger.info("No tables found in database");
            } else {
                logger.info("First table: id={}, number={}, status={}", 
                    tables.get(0).getId(), 
                    tables.get(0).getTableNumber(),
                    tables.get(0).getStatus());
            }

            // Add to model
            model.addAttribute("tables", tables);
            
            // Log view name
            logger.info("Added tables to model, returning view: tables/list");
            return "tables/list";
        } catch (Exception e) {
            logger.error("Error in listTables: ", e);
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            logger.error("Stack trace: ", e);
            throw e;
        }
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String showCreateForm(Model model) {
        logger.info("Showing create table form");
        model.addAttribute("table", new TableEntity());
        return "tables/new";
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String createTable(@ModelAttribute TableEntity table) throws IOException, WriterException {
        logger.info("Creating new table with number: {}", table.getTableNumber());
        tableService.createTable(table);
        return "redirect:/admin/tables";
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String showEditForm(@PathVariable Long id, Model model) {
        logger.info("Showing edit form for table id: {}", id);
        TableEntity table = tableService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid table Id:" + id));
        model.addAttribute("table", table);
        return "tables/form";
    }

    @PostMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateTable(@PathVariable Long id, @ModelAttribute TableEntity table) {
        logger.info("Updating table with id: {}", id);
        tableService.updateTable(id, table);
        return "redirect:/admin/tables";
    }

    @GetMapping("/toggle-status/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String toggleTableStatus(@PathVariable Long id) {
        logger.info("Toggling status for table id: {}", id);
        tableService.toggleTableStatus(id);
        return "redirect:/admin/tables";
    }
}
