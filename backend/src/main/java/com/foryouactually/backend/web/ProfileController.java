package com.foryouactually.backend.web;

import com.foryouactually.backend.recommend.ProfileService;
import com.foryouactually.backend.web.dto.TasteProfileDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/profile")
    public TasteProfileDto profile(@RequestParam String userId) {
        return profiles.build(userId);
    }
}
