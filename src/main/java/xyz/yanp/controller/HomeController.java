package xyz.yanp.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Controller
public class HomeController {

    @GetMapping({"/", "/**/{path:[0-9a-zA-Z-]+}"})
    public String index(HttpServletRequest request, HttpServletResponse response) {
        log.info("common router: " + request.getServletPath());
        return "index";
    }
}
