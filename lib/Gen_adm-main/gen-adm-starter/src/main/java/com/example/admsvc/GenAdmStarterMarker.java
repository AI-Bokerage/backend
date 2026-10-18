package com.example.admsvc;

import org.springframework.stereotype.Component;

/**
 * Trivial bean proving {@link com.example.admsvc.config.GenAdmAutoConfiguration}'s
 * component scan reaches this package from a host app in a different package tree.
 */
@Component
public class GenAdmStarterMarker {

    public String version() {
        return "0.1.0";
    }
}
