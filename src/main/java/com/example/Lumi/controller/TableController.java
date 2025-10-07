package com.example.Lumi.controller;

import com.example.Lumi.model.TableEntity;
import com.example.Lumi.service.TableService;
import com.google.zxing.WriterException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Controller
@RequestMapping("/tables")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String showCreateForm(Model model) {
        model.addAttribute("table", new TableEntity());
        return "tables/new";
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String createTable(@ModelAttribute("table") TableEntity table, Model model) throws IOException, WriterException {
        tableService.saveTable(table);
        return "redirect:/tables/list";
    }

    // Xử lý khi submit form Create
    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String createTable(@ModelAttribute("table") TableEntity table) throws IOException, WriterException {
        tableService.saveTable(table);  // lưu vào DB + generate QR
        return "redirect:/tables/list"; // sau khi lưu thì chuyển sang trang list
    }

    // Hiển thị danh sách bàn
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public String listTables(Model model) {
        model.addAttribute("tables", tableService.getAll());
        return "tables/list";
    }

    // Bật/tắt trạng thái bàn (chỉ employee và admin)
    @PostMapping("/toggle/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public String toggleTableStatus(@PathVariable Long id) {
        tableService.toggleTableStatus(id);
        return "redirect:/tables/list";
    }
}
