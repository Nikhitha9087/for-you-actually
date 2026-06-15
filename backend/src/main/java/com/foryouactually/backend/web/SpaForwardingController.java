package com.foryouactually.backend.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * When the Angular build is served by this app (single-artifact deploy), client-side routes like
 * {@code /discover} or {@code /profile} have no server mapping and would 404 on a hard refresh.
 * This forwards any single-segment, extension-less path to {@code index.html} so the Angular
 * router can take over. {@code /api/**} and real static files (which contain a dot) are untouched.
 */
@Controller
public class SpaForwardingController {

    @RequestMapping(value = {"/", "/{path:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
