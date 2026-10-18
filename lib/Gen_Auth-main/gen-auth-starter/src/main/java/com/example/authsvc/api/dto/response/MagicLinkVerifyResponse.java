package com.example.authsvc.api.dto.response;

public record MagicLinkVerifyResponse(String message) {

    public static MagicLinkVerifyResponse success() {
        return new MagicLinkVerifyResponse("Password reset successful. Please login again.");
    }
}
