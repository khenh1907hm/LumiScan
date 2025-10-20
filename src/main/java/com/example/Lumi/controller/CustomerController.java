package com.example.Lumi.controller;

import com.example.Lumi.service.CategoryService;
import com.example.Lumi.service.MenuItemService;
import com.example.Lumi.service.TableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
//@RequestMapping("/order")
public class CustomerController {
//
//    private final TableService tableService;
//    private final CategoryService categoryService;
//    private final MenuItemService menuItemService;
//
//    @Autowired
//    public CustomerController(TableService tableService,
//                              CategoryService categoryService,
//                              MenuItemService menuItemService) {
//        this.tableService = tableService;
//        this.categoryService = categoryService;
//        this.menuItemService = menuItemService;
//    }
//
//    @GetMapping("/{tableNumber}")
//    public String showMenu(@PathVariable String tableNumber, Model model) {
//        try {
//            // 🔹 Kiểm tra bàn tồn tại
//            var table = tableService.findByTableNumber(tableNumber);
//            if (table.isEmpty()) {
//                model.addAttribute("error", "Bàn không tồn tại");
//                return "error";
//            }
//
//            // 🔹 Kiểm tra trạng thái bàn
//            if (!"available".equalsIgnoreCase(table.get().getStatus())) {
//                model.addAttribute("error", "Bàn đang được sử dụng");
//                return "error";
//            }
//
//            // 🔹 Đưa dữ liệu ra giao diện
//            model.addAttribute("tableNumber", tableNumber);
//            model.addAttribute("categories", categoryService.getAllCategories());
//            model.addAttribute("menuItems", menuItemService.getAllMenuItems());
//
//            return "customer/order";
//
//        } catch (Exception e) {
//            model.addAttribute("error", "Có lỗi xảy ra: " + e.getMessage());
//            return "error";
//        }
//    }
}
