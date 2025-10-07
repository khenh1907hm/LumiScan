package com.example.Lumi.controller;

import com.example.Lumi.model.User;
import com.example.Lumi.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("user", new User());
        return "register"; // return ra file register.html
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        userService.register(user);
        return "redirect:/login";
    }
}
