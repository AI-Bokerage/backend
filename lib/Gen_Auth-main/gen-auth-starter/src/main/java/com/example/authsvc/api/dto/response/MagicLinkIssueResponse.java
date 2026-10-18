package com.example.authsvc.api.dto.response;

public record MagicLinkIssueResponse(String message) {

    public static MagicLinkIssueResponse generic() {
        return new MagicLinkIssueResponse(
                "If the account exists, a reset link has been sent.");
    }
}
