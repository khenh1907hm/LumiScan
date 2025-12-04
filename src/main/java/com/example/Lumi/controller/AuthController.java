package com.example.Lumi.controller;

import com.example.Lumi.model.Order;
import com.example.Lumi.model.TableEntity;
import com.example.Lumi.model.User;
import com.example.Lumi.repository.OrderRepository;
import com.example.Lumi.service.OrderService;
import com.example.Lumi.service.TableService;
import com.example.Lumi.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final TableService tableService;
    private final UserService userService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public AuthController(TableService tableService, UserService userService, 
                         OrderService orderService, OrderRepository orderRepository) {
        this.tableService = tableService;
        this.userService = userService;
        this.orderService = orderService;
        this.orderRepository = orderRepository;
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
    public String home() {
        // Luôn hiển thị trang quét mã QR, không redirect
        logger.info("Showing home page (QR scanner)");
        return "index";
    }
    
    @GetMapping("/home")
    public String homePage() {
        // Endpoint riêng để customer có thể về trang quét mã mà không bị redirect
        logger.info("Accessing home page (QR scanner) - no redirect");
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
            
            // Initialize default values
            long totalTables = 0;
            long activeTables = 0;
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal profit = BigDecimal.ZERO;
            long productsSold = 0;
            List<Order> recentOrders = new ArrayList<>();
            List<User> recentCustomers = new ArrayList<>();
            BigDecimal[] weeklyRevenue = new BigDecimal[7];
            // Initialize array với giá trị mặc định
            for (int i = 0; i < 7; i++) {
                weeklyRevenue[i] = BigDecimal.ZERO;
            }
            java.util.Map<Long, BigDecimal> orderTotals = new java.util.HashMap<>();
            java.util.Map<Long, String> orderFormattedDates = new java.util.HashMap<>();
            String formattedCurrentDate = "";
            String formattedCurrentDateTime = "";
            
            try {
                // Load tables count
                try {
                    totalTables = tableService.countAllTables();
                    logger.debug("Total tables count: {}", totalTables);
                } catch (Exception e) {
                    logger.error("Error counting total tables", e);
                }
                
                try {
                    activeTables = tableService.countActiveTables();
                    logger.debug("Active tables count: {}", activeTables);
                } catch (Exception e) {
                    logger.error("Error counting active tables", e);
                }
                
                // Calculate revenue (from paid orders this month) - OPTIMIZE: Chỉ load orders một lần
                try {
                    LocalDate startOfMonth = LocalDate.now().withDayOfMonth(1);
                    
                    // OPTIMIZE: Load tất cả orders một lần duy nhất (tránh multiple queries)
                    // Giới hạn số lượng orders để tránh timeout
                    List<Order> allOrders = new ArrayList<>();
                    try {
                        allOrders = orderRepository.findAll();
                        logger.debug("Loaded {} total orders from database", allOrders.size());
                        
                        // Nếu có quá nhiều orders, chỉ lấy 1000 orders mới nhất để tránh lag
                        if (allOrders.size() > 1000) {
                            logger.warn("Too many orders ({}), limiting to 1000 most recent", allOrders.size());
                            allOrders = allOrders.stream()
                                .filter(o -> o != null && o.getCreatedAt() != null)
                                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                                .limit(1000)
                                .collect(Collectors.toList());
                        }
                    } catch (Exception e) {
                        logger.error("Error loading orders", e);
                        // Nếu lỗi, set empty list để tránh crash
                        allOrders = new ArrayList<>();
                    }
                    
                    // Filter paid orders this month từ allOrders
                    List<Order> paidOrdersThisMonth = allOrders.stream()
                        .filter(o -> o != null && o.getStatus() == Order.Status.paid && o.getCreatedAt() != null)
                        .filter(o -> o.getCreatedAt().toLocalDate().isAfter(startOfMonth.minusDays(1)))
                        .collect(Collectors.toList());
                    
                    logger.debug("Found {} paid orders this month", paidOrdersThisMonth.size());
                    
                    if (!paidOrdersThisMonth.isEmpty()) {
                        totalRevenue = paidOrdersThisMonth.stream()
                            .filter(o -> o.getItems() != null)
                            .map(order -> {
                                try {
                                    return orderService.calculateTotal(order);
                                } catch (Exception e) {
                                    logger.warn("Error calculating total for order {}: {}", order.getId(), e.getMessage());
                                    return BigDecimal.ZERO;
                                }
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    }
                    
                    // Calculate profit (70% of revenue as example)
                    profit = totalRevenue.multiply(BigDecimal.valueOf(0.7));
                    
                    // Count products sold (sum of all order items quantities)
                    productsSold = paidOrdersThisMonth.stream()
                        .filter(o -> o.getItems() != null)
                        .flatMap(o -> o.getItems().stream())
                        .filter(item -> item != null)
                        .mapToLong(item -> item.getQuantity())
                        .sum();
                    
                    // Get recent orders (last 5) - đã có allOrders từ trên
                    recentOrders = allOrders.stream()
                        .filter(o -> o != null && o.getCreatedAt() != null)
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                        .limit(5)
                        .collect(Collectors.toList());
                    
                    logger.debug("Selected {} recent orders for display", recentOrders.size());
                    
                    // Calculate totals for each order and store in a map
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, H:mm", new Locale("vi", "VN"));
                    
                    for (Order order : recentOrders) {
                        try {
                            // Kiểm tra null và lazy loading
                            if (order != null && order.getId() != null) {
                                try {
                                    // Force load items nếu cần (tránh lazy loading exception)
                                    if (order.getItems() != null) {
                                        // Touch items để force load
                                        int itemCount = order.getItems().size();
                                        if (itemCount > 0) {
                                            BigDecimal total = orderService.calculateTotal(order);
                                            orderTotals.put(order.getId(), total);
                                        } else {
                                            orderTotals.put(order.getId(), BigDecimal.ZERO);
                                        }
                                    } else {
                                        orderTotals.put(order.getId(), BigDecimal.ZERO);
                                    }
                                } catch (Exception e) {
                                    logger.warn("Error calculating total for order {}: {}", order.getId(), e.getMessage());
                                    orderTotals.put(order.getId(), BigDecimal.ZERO);
                                }
                                
                                // Format order date
                                if (order.getCreatedAt() != null) {
                                    try {
                                        String formattedDate = order.getCreatedAt().format(dateTimeFormatter);
                                        orderFormattedDates.put(order.getId(), formattedDate);
                                    } catch (Exception e) {
                                        logger.warn("Error formatting date for order {}: {}", order.getId(), e.getMessage());
                                        orderFormattedDates.put(order.getId(), "");
                                    }
                                } else {
                                    orderFormattedDates.put(order.getId(), "");
                                }
                            }
                        } catch (Exception e) {
                            logger.error("Error processing order: {}", e.getMessage(), e);
                            // Set defaults để tránh crash
                            if (order != null && order.getId() != null) {
                                orderTotals.put(order.getId(), BigDecimal.ZERO);
                                orderFormattedDates.put(order.getId(), "");
                            }
                        }
                    }
                    
                    // Get recent customers (from orders)
                    recentCustomers = recentOrders.stream()
                        .filter(o -> o.getTable() != null)
                        .map(o -> o.getTable())
                        .distinct()
                        .limit(5)
                        .map(t -> {
                            // Create a simple user representation from table
                            User u = new User();
                            u.setFullName("Bàn " + t.getTableNumber());
                            return u;
                        })
                        .collect(Collectors.toList());
                    
                    // Weekly revenue data for chart (last 7 days)
                    for (int i = 0; i < 7; i++) {
                        try {
                            LocalDate date = LocalDate.now().minusDays(6 - i);
                            BigDecimal dayRevenue = allOrders.stream()
                                .filter(o -> o != null && o.getStatus() == Order.Status.paid && o.getCreatedAt() != null)
                                .filter(o -> {
                                    try {
                                        return o.getCreatedAt().toLocalDate().equals(date);
                                    } catch (Exception e) {
                                        logger.warn("Error comparing date for order: {}", e.getMessage());
                                        return false;
                                    }
                                })
                                .filter(o -> {
                                    try {
                                        return o.getItems() != null && !o.getItems().isEmpty();
                                    } catch (Exception e) {
                                        logger.warn("Error accessing items for order: {}", e.getMessage());
                                        return false;
                                    }
                                })
                                .map(order -> {
                                    try {
                                        return orderService.calculateTotal(order);
                                    } catch (Exception e) {
                                        logger.warn("Error calculating total for weekly revenue: {}", e.getMessage());
                                        return BigDecimal.ZERO;
                                    }
                                })
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                            weeklyRevenue[i] = dayRevenue != null ? dayRevenue : BigDecimal.ZERO;
                        } catch (Exception e) {
                            logger.error("Error calculating weekly revenue for day {}: {}", i, e.getMessage());
                            weeklyRevenue[i] = BigDecimal.ZERO;
                        }
                    }
                    
                    // Format dates for template
                    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", new Locale("vi", "VN"));
                    formattedCurrentDate = LocalDate.now().format(dateFormatter);
                    formattedCurrentDateTime = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("vi", "VN"))) + ", " + LocalTime.now().format(DateTimeFormatter.ofPattern("H:mm"));
                } catch (Exception e) {
                    logger.error("Error calculating revenue and statistics", e);
                }
                
                // Log all data before adding to model
                logger.info("=== Dashboard Data Summary ===");
                logger.info("Revenue: {}", totalRevenue);
                logger.info("Profit: {}", profit);
                logger.info("Products Sold: {}", productsSold);
                logger.info("Recent Orders Count: {}", recentOrders != null ? recentOrders.size() : 0);
                logger.info("Recent Customers Count: {}", recentCustomers != null ? recentCustomers.size() : 0);
                logger.info("Weekly Revenue Array: {}", java.util.Arrays.toString(weeklyRevenue));
                logger.info("Order Totals Map Size: {}", orderTotals != null ? orderTotals.size() : 0);
                logger.info("Order Formatted Dates Map Size: {}", orderFormattedDates != null ? orderFormattedDates.size() : 0);
                logger.info("Formatted Current Date: {}", formattedCurrentDate);
                logger.info("Formatted Current DateTime: {}", formattedCurrentDateTime);
                
                if (recentOrders != null && !recentOrders.isEmpty()) {
                    logger.debug("Recent Orders Details:");
                    for (Order order : recentOrders) {
                        logger.debug("  Order ID: {}, Status: {}, CreatedAt: {}, Items: {}", 
                            order.getId(), 
                            order.getStatus(), 
                            order.getCreatedAt(),
                            order.getItems() != null ? order.getItems().size() : 0);
                    }
                } else {
                    logger.warn("WARNING: recentOrders is null or empty!");
                }
                
                if (orderTotals != null && !orderTotals.isEmpty()) {
                    logger.debug("Order Totals Details:");
                    orderTotals.forEach((id, total) -> {
                        logger.debug("  Order ID {}: Total = {}", id, total);
                    });
                } else {
                    logger.warn("WARNING: orderTotals is null or empty!");
                }
                
                model.addAttribute("revenue", totalRevenue);
                model.addAttribute("profit", profit);
                model.addAttribute("productsSold", productsSold);
                model.addAttribute("recentOrders", recentOrders);
                model.addAttribute("recentCustomers", recentCustomers);
                model.addAttribute("weeklyRevenue", weeklyRevenue);
                model.addAttribute("orderTotals", orderTotals);
                model.addAttribute("orderFormattedDates", orderFormattedDates);
                model.addAttribute("formattedCurrentDate", formattedCurrentDate);
                model.addAttribute("formattedCurrentDateTime", formattedCurrentDateTime);
                
                logger.info("All attributes added to model successfully");
                
            } catch (Exception e) {
                logger.error("=== ERROR loading dashboard statistics ===", e);
                logger.error("Exception type: {}", e.getClass().getName());
                logger.error("Exception message: {}", e.getMessage());
                if (e.getCause() != null) {
                    logger.error("Caused by: {}", e.getCause().getMessage());
                }
                logger.error("Stack trace:", e);
                // Set default values if service fails
                model.addAttribute("totalTables", 0);
                model.addAttribute("activeTables", 0);
                model.addAttribute("revenue", BigDecimal.ZERO);
                model.addAttribute("profit", BigDecimal.ZERO);
                model.addAttribute("productsSold", 0);
                model.addAttribute("recentOrders", Collections.emptyList());
                model.addAttribute("recentCustomers", Collections.emptyList());
                model.addAttribute("weeklyRevenue", new BigDecimal[7]);
                model.addAttribute("orderTotals", new java.util.HashMap<>());
                model.addAttribute("orderFormattedDates", new java.util.HashMap<>());
                model.addAttribute("formattedCurrentDate", "");
                model.addAttribute("formattedCurrentDateTime", "");
            }
            
            // Ensure all required attributes are in model
            model.addAttribute("user", currentUser);
            model.addAttribute("totalTables", totalTables);
            model.addAttribute("activeTables", activeTables);
            
            // Final check: Log all model attributes
            logger.info("=== Final Model Attributes Check ===");
            logger.info("User: {}", currentUser != null ? currentUser.getUsername() : "NULL");
            logger.info("Total Tables: {}", totalTables);
            logger.info("Active Tables: {}", activeTables);
            logger.info("Model contains 'revenue': {}", model.containsAttribute("revenue"));
            logger.info("Model contains 'profit': {}", model.containsAttribute("profit"));
            logger.info("Model contains 'productsSold': {}", model.containsAttribute("productsSold"));
            logger.info("Model contains 'recentOrders': {}", model.containsAttribute("recentOrders"));
            logger.info("Model contains 'recentCustomers': {}", model.containsAttribute("recentCustomers"));
            logger.info("Model contains 'weeklyRevenue': {}", model.containsAttribute("weeklyRevenue"));
            logger.info("Model contains 'orderTotals': {}", model.containsAttribute("orderTotals"));
            logger.info("Model contains 'orderFormattedDates': {}", model.containsAttribute("orderFormattedDates"));
            logger.info("Model contains 'formattedCurrentDate': {}", model.containsAttribute("formattedCurrentDate"));
            logger.info("Model contains 'formattedCurrentDateTime': {}", model.containsAttribute("formattedCurrentDateTime"));
            
            logger.info("=== ALL ATTRIBUTES SET - RETURNING TEMPLATE ===");
            logger.info("Template name: admin/dashboard");
            return "admin/dashboard";
        } catch (Exception e) {
            logger.error("=== CRITICAL ERROR loading admin dashboard ===", e);
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: {}", e.getCause().getMessage());
            }
            logger.error("Full stack trace:", e);
            
            // Set safe default values to prevent template errors
            try {
                User currentUser = userService.findByUsername(authentication.getName()).orElse(null);
                model.addAttribute("user", currentUser);
            } catch (Exception ex) {
                logger.error("Error loading user for error page", ex);
                model.addAttribute("user", null);
            }
            
            model.addAttribute("totalTables", 0);
            model.addAttribute("activeTables", 0);
            model.addAttribute("revenue", BigDecimal.ZERO);
            model.addAttribute("profit", BigDecimal.ZERO);
            model.addAttribute("productsSold", 0);
            model.addAttribute("recentOrders", Collections.emptyList());
            model.addAttribute("recentCustomers", Collections.emptyList());
            BigDecimal[] defaultWeeklyRevenue = new BigDecimal[7];
            for (int i = 0; i < 7; i++) {
                defaultWeeklyRevenue[i] = BigDecimal.ZERO;
            }
            model.addAttribute("weeklyRevenue", defaultWeeklyRevenue);
            model.addAttribute("orderTotals", new java.util.HashMap<>());
            model.addAttribute("orderFormattedDates", new java.util.HashMap<>());
            model.addAttribute("formattedCurrentDate", "");
            model.addAttribute("formattedCurrentDateTime", "");
            model.addAttribute("error", "Có lỗi xảy ra khi tải dashboard: " + e.getMessage());
            
            // Still return the template so user can see error message
            return "admin/dashboard";
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
                long totalTables = 0;
                long activeTables = 0;
                List<TableEntity> allTables = new ArrayList<>();
                List<TableEntity> occupiedTables = new ArrayList<>();
                
                try {
                    totalTables = tableService.countAllTables();
                    logger.debug("Total tables count: {}", totalTables);
                } catch (Exception e) {
                    logger.error("Error counting total tables", e);
                }
                
                try {
                    activeTables = tableService.countActiveTables();
                    logger.debug("Active tables count: {}", activeTables);
                } catch (Exception e) {
                    logger.error("Error counting active tables", e);
                }
                
                try {
                    // Load all tables, especially occupied ones
                    allTables = tableService.findAllTables();
                    if (allTables != null) {
                        occupiedTables = allTables.stream()
                            .filter(t -> t != null && t.getStatus() != null && "occupied".equalsIgnoreCase(t.getStatus()))
                            .collect(Collectors.toList());
                        logger.debug("Loaded {} occupied tables out of {} total", occupiedTables.size(), allTables.size());
                    } else {
                        logger.warn("findAllTables returned null");
                        allTables = new ArrayList<>();
                    }
                } catch (Exception e) {
                    logger.error("Error loading tables list", e);
                    allTables = new ArrayList<>();
                    occupiedTables = new ArrayList<>();
                }
                
                model.addAttribute("totalTables", totalTables);
                model.addAttribute("activeTables", activeTables);
                model.addAttribute("tables", allTables);
                model.addAttribute("occupiedTables", occupiedTables);
            } catch (Exception e) {
                logger.error("Error loading table statistics", e);
                // Set default values if table service fails
                model.addAttribute("totalTables", 0);
                model.addAttribute("activeTables", 0);
                model.addAttribute("tables", Collections.emptyList());
                model.addAttribute("occupiedTables", Collections.emptyList());
            }
            
            model.addAttribute("user", currentUser);
            logger.info("Rendering employee dashboard template");
            return "employee/dashboard";
        } catch (Exception e) {
            logger.error("=== CRITICAL ERROR loading employee dashboard ===", e);
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: {}", e.getCause().getMessage());
            }
            logger.error("Full stack trace:", e);
            
            // Set safe default values to prevent template errors
            model.addAttribute("user", null);
            model.addAttribute("totalTables", 0);
            model.addAttribute("activeTables", 0);
            model.addAttribute("tables", Collections.emptyList());
            model.addAttribute("occupiedTables", Collections.emptyList());
            model.addAttribute("error", "Có lỗi xảy ra khi tải dashboard: " + e.getMessage());
            
            // Still return the template so user can see error message
            return "employee/dashboard";
        }
    }
}
