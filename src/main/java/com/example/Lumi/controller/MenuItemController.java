package com.example.Lumi.controller;

import com.example.Lumi.model.Category;
import com.example.Lumi.model.MenuItem;
import com.example.Lumi.service.CategoryService;
import com.example.Lumi.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/admin/menu")
@PreAuthorize("hasRole('ADMIN')")
public class MenuItemController {

    private MenuItemService menuItemService;
    private CategoryService categoryService;

    @Autowired
    public MenuItemController(MenuItemService menuItemService, CategoryService categoryService) {
        this.menuItemService = menuItemService;
        this.categoryService = categoryService;
    }
    // Thư mục lưu ảnh
    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/menu/";

    // ✅ Trang danh sách món ăn
    @GetMapping
    public String listMenuItems(Model model) {
        model.addAttribute("menuItems", menuItemService.getAllMenuItems());
        return "admin/menu/list";  // trỏ đến templates/admin/menu/list.html
    }

    // ✅ Trang thêm món ăn mới
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        MenuItem menuItem = new MenuItem();
        menuItem.setCategory(new Category());
        model.addAttribute("menuItem", menuItem);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/menu/form";
    }


    // ✅ Xử lý thêm món ăn
    @PostMapping("/create")
    public String createMenuItem(
            @ModelAttribute("menuItem") MenuItem menuItem,
            BindingResult result,
            @RequestParam("imageFile") MultipartFile imageFile,
            Model model) {

        // ---------------- Debug ----------------
        System.out.println("DEBUG: MenuItem nhận từ form:");
        System.out.println("  name: " + menuItem.getName());
        System.out.println("  price: " + menuItem.getPrice());
        if(menuItem.getCategory() != null) {
            System.out.println("  category id: " + menuItem.getCategory().getId());
        } else {
            System.out.println("  category object is null");
        }
        System.out.println("  status: " + menuItem.getStatus());
        System.out.println("-------------------------------------");

        // binding error
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategories());
            return "admin/menu/form";
        }

        // Nạp Category thật
        if (menuItem.getCategory() != null && menuItem.getCategory().getId() != null) {
            Long catId = menuItem.getCategory().getId();
            System.out.println("DEBUG: Loading Category từ DB với ID = " + catId);
            Category category = categoryService.getCategoryById(catId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có ID " + catId));
            menuItem.setCategory(category);
            System.out.println("DEBUG: Category được gán: " + category.getName());
        } else {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("error", "Vui lòng chọn danh mục.");
            System.out.println("DEBUG: Category bị null hoặc chưa chọn");
            return "admin/menu/form";
        }

        // ✅ Xử lý upload ảnh nếu có
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String original = imageFile.getOriginalFilename();
                String cleanName = Paths.get(original).getFileName().toString();
                String uniqueName = UUID.randomUUID() + "_" + cleanName;
                Path filePath = uploadPath.resolve(uniqueName);

                Files.copy(imageFile.getInputStream(), filePath);
                menuItem.setImageUrl("/uploads/menu/" + uniqueName);

            } catch (IOException e) {
                e.printStackTrace();
                model.addAttribute("categories", categoryService.getAllCategories());
                model.addAttribute("error", "Không thể lưu ảnh: " + e.getMessage());
                return "admin/menu/form";
            }
        }

        // Lưu menuItem vào DB
        menuItemService.createMenuItem(menuItem);

        return "redirect:/admin/menu";
    }



    // ✅ Trang chỉnh sửa món ăn
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        MenuItem menuItem = menuItemService.getMenuItemById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn có id " + id));
        model.addAttribute("menuItem", menuItem);
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/menu/form"; // dùng chung form
    }

    // ✅ Xử lý cập nhật món ăn
    @PostMapping("/edit/{id}")
    public String updateMenuItem(@PathVariable Long id,
                                 @ModelAttribute("menuItem") MenuItem menuItem,
                                 @RequestParam("imageFile") MultipartFile imageFile,
                                 BindingResult result,
                                 Model model) {

        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategories());
            return "admin/menu/form";
        }

        // ✅ Nạp Category thật từ DB
        if (menuItem.getCategory() != null && menuItem.getCategory().getId() != null) {
            Long catId = menuItem.getCategory().getId();
            Category category = categoryService.getCategoryById(catId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có ID " + catId));
            menuItem.setCategory(category);
        } else {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("error", "Vui lòng chọn danh mục.");
            return "admin/menu/form";
        }

        // ✅ Xử lý upload ảnh
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String original = imageFile.getOriginalFilename();
                String cleanName = Paths.get(original).getFileName().toString();
                String uniqueName = UUID.randomUUID() + "_" + cleanName;
                Path filePath = uploadPath.resolve(uniqueName);

                Files.copy(imageFile.getInputStream(), filePath);
                menuItem.setImageUrl("/uploads/menu/" + uniqueName);

            } catch (IOException e) {
                e.printStackTrace();
                model.addAttribute("categories", categoryService.getAllCategories());
                model.addAttribute("error", "Không thể lưu ảnh: " + e.getMessage());
                return "admin/menu/form";
            }
        } else {
            // Giữ lại ảnh cũ nếu không upload mới
            MenuItem existing = menuItemService.getMenuItemById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn có id " + id));
            menuItem.setImageUrl(existing.getImageUrl());
        }

        // Lưu update
        menuItemService.updateMenuItem(id, menuItem);

        return "redirect:/admin/menu";
    }


    // ✅ Xóa món ăn
    @GetMapping("/delete/{id}")
    public String deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return "redirect:/admin/menu";
    }
}
