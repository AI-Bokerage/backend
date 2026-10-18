package com.example.authsvc.api.dto.response;

public record LogoutResponse(String message) {

    public static LogoutResponse success() {
        return new LogoutResponse("Logged out successfully");
    }
}
