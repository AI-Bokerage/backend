package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
// THIS FILE NEED TO BE CHANGED IN PRODUCTION
@Data
@ConfigurationProperties(prefix = "app.cookie")
public class CookieProperties {

    private String  domain   = "localhost";
    private boolean secure   = false;
    private boolean httpOnly = true;
    private String  sameSite = "Lax";
    private String  path     = "/";
}