package com.example.Lumi.controller;

import com.example.Lumi.model.User;
import com.example.Lumi.service.TableService;
import com.example.Lumi.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        String role = authentication.getAuthorities().stream()
            .findFirst()
            .map(a -> a.getAuthority())
            .orElse("");

        if (role.equals("ROLE_ADMIN")) {
            return "redirect:/admin/dashboard";
        } else if (role.equals("ROLE_EMPLOYEE")) {
            return "redirect:/employee/dashboard";
        }

        return "redirect:/login";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model, Authentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "admin/dashboard";
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model, Authentication authentication) {
        if (!authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))) {
            return "redirect:/login";
        }

        User user = userService.findByUsername(authentication.getName()).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "employee/dashboard";
    }
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
        model.addAttribute("user", currentUser);
        model.addAttribute("totalTables", tableService.countAllTables());
        model.addAttribute("activeTables", tableService.countActiveTables());
        return "employee/dashboard";
    }
                case ADMIN:
                    return "dashboard";
                case EMPLOYEE:
                    return "employee/table";
                default:
                    return "dashboard";
            }
        }
        
        return "dashboard";
    }
}
