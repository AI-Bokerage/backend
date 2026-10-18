package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.MagicLinkIssueRequest;
import com.example.authsvc.api.dto.request.MagicLinkVerifyRequest;
import com.example.authsvc.api.dto.response.MagicLinkIssueResponse;
import com.example.authsvc.api.dto.response.MagicLinkVerifyResponse;

public interface SuperAdminMagicLinkService {

    MagicLinkIssueResponse issue(MagicLinkIssueRequest request, String ip);

    MagicLinkVerifyResponse verify(MagicLinkVerifyRequest request);
}
