// gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntProperties.java
package com.example.tnt_svc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gentnt")
public class GenTntProperties {

    private String internalSecret;

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }
}
