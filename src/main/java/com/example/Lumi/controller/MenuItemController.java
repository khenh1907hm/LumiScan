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
        model.addAttribute("menuItem", new MenuItem());
        model.addAttribute("categories", categoryService.getAllCategories());
        return "admin/menu/form"; // templates/admin/menu/form.html
    }

    // ✅ Xử lý thêm món ăn
    @PostMapping("/create")
//    @PostMapping("/create")
    public String createMenuItem(
            @ModelAttribute("menuItem") MenuItem menuItem,
            BindingResult result,
            @RequestParam("imageFile") MultipartFile imageFile,
            Model model) {

        // ✅ Nếu có lỗi binding, trả lại form (phải gửi lại danh sách categories)
        if (result.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategories());
            return "admin/menu/form";
        }

        // ✅ Nạp category thật từ DB (chứ không chỉ có id)
        try {
            if (menuItem.getCategory() != null && menuItem.getCategory().getId() != null) {
                Long catId = menuItem.getCategory().getId();
                Category category = categoryService.getCategoryById(catId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục có ID " + catId));
                menuItem.setCategory(category); // Gán lại category thật
            } else {
                model.addAttribute("categories", categoryService.getAllCategories());
                model.addAttribute("error", "Vui lòng chọn danh mục.");
                return "admin/menu/form";
            }
        } catch (Exception ex) {
            model.addAttribute("categories", categoryService.getAllCategories());
            model.addAttribute("error", "Lỗi khi nạp danh mục: " + ex.getMessage());
            return "admin/menu/form";
        }

        // ✅ Xử lý upload ảnh nếu có
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                // Lấy tên file gốc an toàn
                String original = imageFile.getOriginalFilename();
                String cleanName = Paths.get(original).getFileName().toString();

                // Sinh tên file duy nhất tránh trùng lặp
                String uniqueName = UUID.randomUUID() + "_" + cleanName;
                Path filePath = uploadPath.resolve(uniqueName);

                // Sao chép file vào thư mục upload
                Files.copy(imageFile.getInputStream(), filePath);

                // Gán đường dẫn ảnh để hiển thị
                menuItem.setImageUrl("/uploads/menu/" + uniqueName);

            } catch (IOException e) {
                e.printStackTrace();
                model.addAttribute("categories", categoryService.getAllCategories());
                model.addAttribute("error", "Không thể lưu ảnh: " + e.getMessage());
                return "admin/menu/form";
            }
        }

        // ✅ Lưu menuItem (đã có category và imageUrl)
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
                                 BindingResult result) {
        if (result.hasErrors()) {
            return "admin/menu/form";
        }

        // Nếu có file ảnh mới thì cập nhật
        if (!imageFile.isEmpty()) {
            try {
                Path uploadPath = Paths.get(UPLOAD_DIR);
                if (!Files.exists(uploadPath)) {
                    Files.createDirectories(uploadPath);
                }

                String fileName = imageFile.getOriginalFilename();
                Path filePath = uploadPath.resolve(fileName);
                Files.write(filePath, imageFile.getBytes());

                menuItem.setImageUrl("/uploads/menu/" + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            // Giữ lại ảnh cũ nếu không upload mới
            MenuItem existing = menuItemService.getMenuItemById(id).orElseThrow();
            menuItem.setImageUrl(existing.getImageUrl());
        }

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
