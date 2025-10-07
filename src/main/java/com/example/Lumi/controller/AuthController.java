package com.example.Lumi.controller;

import com.example.Lumi.model.User;
import com.example.Lumi.service.TableService;
import com.example.Lumi.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
public class AuthController {

    private final TableService tableService;
    private final UserService userService;

    public AuthController(TableService tableService, UserService userService) {
        this.tableService = tableService;
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
            return "redirect:/dashboard";
        }
        return "login";
    }

    @GetMapping("/dashboard")
    public void dashboard(HttpServletResponse response, Authentication authentication) throws IOException {
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getName().equals("anonymousUser")) {
            response.sendRedirect("/login");
            return;
        }

        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            response.sendRedirect("/admin/dashboard");
        } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
            response.sendRedirect("/employee/dashboard");
        } else {
            response.sendRedirect("/customer/dashboard");
        }
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model, Authentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return "redirect:/login";
        }
        
        User currentUser = userService.findByUsername(authentication.getName()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "admin/dashboard";
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
            return "redirect:/login";
        }
        
        User currentUser = userService.findByUsername(authentication.getName()).orElse(null);
        if (currentUser == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "employee/dashboard";
    }
}
