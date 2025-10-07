package com.example.Lumi.controller;

import com.example.Lumi.service.TableService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/order")
public class CustomerController {

    private final TableService tableService;

    public CustomerController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/{tableNumber}")
    public String showMenu(@PathVariable String tableNumber, Model model) {
        try {
            // Kiểm tra bàn có tồn tại và đang hoạt động không
            var tables = tableService.getAll();
            var table = tables.stream()
                    .filter(t -> t.getTableNumber().equals(tableNumber))
                    .findFirst();
            
            if (table.isEmpty()) {
                model.addAttribute("error", "Bàn không tồn tại");
                return "error";
            }
            
            if (!"available".equals(table.get().getStatus())) {
                model.addAttribute("error", "Bàn đang được sử dụng");
                return "error";
            }
            
            model.addAttribute("tableNumber", tableNumber);
            // TODO: Load menu items from database
            return "customer/order";
        } catch (Exception e) {
            model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
            return "error";
        }
    }
}
