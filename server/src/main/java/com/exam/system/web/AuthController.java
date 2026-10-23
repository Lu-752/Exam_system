package com.exam.system.web;

import com.exam.common.R;
import com.exam.security.TokenStore;
import com.exam.system.dto.LoginRequest;
import com.exam.system.dto.LoginResponse;
import com.exam.system.dto.MenuNode;
import com.exam.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req.username().trim(), req.password()));
    }

    @GetMapping("/me")
    public R<LoginResponse.UserInfo> me(Authentication authentication) {
        return R.ok(authService.me(session(authentication)));
    }

    @GetMapping("/menus")
    public R<List<MenuNode>> menus(Authentication authentication) {
        return R.ok(authService.menus(session(authentication).userId()));
    }

    @PostMapping("/logout")
    public R<Void> logout(Authentication authentication) {
        authService.logout(String.valueOf(authentication.getCredentials()));
        return R.ok();
    }

    private TokenStore.Session session(Authentication authentication) {
        return (TokenStore.Session) authentication.getDetails();
    }
}
