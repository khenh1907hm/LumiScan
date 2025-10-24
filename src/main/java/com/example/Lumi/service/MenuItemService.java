package com.example.Lumi.service;

import com.example.Lumi.model.MenuItem;
import com.example.Lumi.repository.MenuItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Transactional
@Service
public class MenuItemService {

    @Autowired
    private MenuItemRepository menuItemRepository;

    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    public Optional<MenuItem> getMenuItemById(Long id) {
        return menuItemRepository.findById(id);
    }

    // Phương thức mới: Trả về MenuItem trực tiếp, ném ngoại lệ nếu không tìm thấy
    public MenuItem getMenuItemByIdOrThrow(Long id) {
        return menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Món ăn không tồn tại: " + id));
    }

    public MenuItem createMenuItem(MenuItem menuItem) {
        return menuItemRepository.save(menuItem);
    }

    public MenuItem updateMenuItem(Long id, MenuItem updatedItem) {
        return menuItemRepository.findById(id)
                .map(item -> {
                    item.setName(updatedItem.getName());
                    item.setDescription(updatedItem.getDescription());
                    item.setPrice(updatedItem.getPrice());
                    item.setImageUrl(updatedItem.getImageUrl());
                    item.setCategory(updatedItem.getCategory());
                    item.setStatus(updatedItem.getStatus());
                    return menuItemRepository.save(item);
                })
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món có id: " + id));
    }

    public void deleteMenuItem(Long id) {
        menuItemRepository.deleteById(id);
    }
}