package com.example.Lumi.controller;

import com.example.Lumi.service.TableService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/employee")
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class EmployeeController {

    private final TableService tableService;

    public EmployeeController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/tables")
    public String manageTables(Model model) {
        model.addAttribute("tables", tableService.getAll());
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "employee/tables";
    }
}
