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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final TableService tableService;
    private final UserService userService;

    public AuthController(TableService tableService, UserService userService) {
        this.tableService = tableService;
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("Login page access - Current authentication: {}", auth);
        
        // Luôn cho phép truy cập trang login
        logger.info("Showing login page");
        return "login";
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        logger.info("=== Starting home page processing ===");
        logger.debug("Home page access - Authentication details: {}", authentication);
        
        if (authentication != null && authentication.isAuthenticated() 
            && !authentication.getName().equals("anonymousUser")) {
            
            logger.info("Authenticated user: {}", authentication.getName());
            logger.info("User authorities: {}", authentication.getAuthorities());
            
            if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
                logger.info("Redirecting admin to admin dashboard");
                return "redirect:/admin/dashboard";
            } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
                logger.info("Redirecting employee to employee dashboard");
                return "redirect:/employee/dashboard";
            }
        }
        
        logger.info("Showing home page");
        return "index";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        logger.info("Dashboard access attempt - Authentication: {}", authentication);
        
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication.getName().equals("anonymousUser")) {
            logger.warn("Unauthenticated access attempt to dashboard");
            return "redirect:/login";
        }

        logger.info("User authorities: {}", authentication.getAuthorities());
        
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            logger.info("Redirecting admin to admin dashboard");
            return "redirect:/admin/dashboard";
        } else if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
            logger.info("Redirecting employee to employee dashboard");
            return "redirect:/employee/dashboard";
        } else {
            logger.info("Redirecting to home page");
            return "redirect:/";
        }
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model, Authentication authentication) {
        logger.info("=== Starting admin dashboard processing ===");
        logger.debug("Admin dashboard access - Full authentication details: {}", authentication);
        
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            logger.warn("Access denied: User {} attempted to access admin dashboard without ROLE_ADMIN", 
                       authentication.getName());
            return "redirect:/login";
        }
        
        try {
            logger.debug("Finding user in database: {}", authentication.getName());
            User currentUser = userService.findByUsername(authentication.getName()).orElse(null);
            if (currentUser == null) {
                logger.error("User not found in database: {}", authentication.getName());
                return "redirect:/login";
            }
            
            logger.info("Loading admin dashboard data for user: {}", currentUser.getUsername());
            try {
                long totalTables = tableService.countAllTables();
                logger.debug("Total tables count: {}", totalTables);
                model.addAttribute("totalTables", totalTables);
                
                long activeTables = tableService.countActiveTables();
                logger.debug("Active tables count: {}", activeTables);
                model.addAttribute("activeTables", activeTables);
            } catch (Exception e) {
                logger.error("Error loading table statistics", e);
                // Set default values if table service fails
                model.addAttribute("totalTables", 0);
                model.addAttribute("activeTables", 0);
            }
            
            model.addAttribute("user", currentUser);
            logger.info("Rendering admin dashboard template");
            return "admin/dashboard";
        } catch (Exception e) {
            logger.error("Error loading admin dashboard", e);
            throw e;
        }
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        logger.info("=== Starting employee dashboard processing ===");
        logger.debug("Employee dashboard access - Full authentication details: {}", authentication);
        
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
            logger.warn("Access denied: User {} attempted to access employee dashboard without ROLE_EMPLOYEE", 
                       authentication.getName());
            return "redirect:/login";
        }
        
        try {
            logger.debug("Finding employee in database: {}", authentication.getName());
            User currentUser = userService.findByUsername(authentication.getName()).orElse(null);
            if (currentUser == null) {
                logger.error("Employee not found in database: {}", authentication.getName());
                return "redirect:/login";
            }
            
            logger.info("Loading employee dashboard data for user: {}", currentUser.getUsername());
            try {
                long totalTables = tableService.countAllTables();
                logger.debug("Total tables count: {}", totalTables);
                model.addAttribute("totalTables", totalTables);
                
                long activeTables = tableService.countActiveTables();
                logger.debug("Active tables count: {}", activeTables);
                model.addAttribute("activeTables", activeTables);
            } catch (Exception e) {
                logger.error("Error loading table statistics", e);
                // Set default values if table service fails
                model.addAttribute("totalTables", 0);
                model.addAttribute("activeTables", 0);
            }
            
            model.addAttribute("user", currentUser);
            logger.info("Rendering employee dashboard template");
            return "employee/dashboard";
        } catch (Exception e) {
            logger.error("Error loading employee dashboard", e);
            throw e;
        }
    }
}
