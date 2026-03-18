package com.td.web.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SwaggerUiController {

    @GetMapping("/swagger-ui.html")
    public String swaggerUi() {
        // Keep legacy URL stable and route to custom page with quick token tools.
        return "redirect:/swagger-custom/index.html";
    }
}
