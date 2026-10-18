package com.example.gendemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-auth-starter auto-configures correctly from
 * outside its own package — this class lives in {@code com.example.gendemo},
 * not {@code com.example.authsvc}, specifically so the starter's beans can only
 * be found via {@link com.example.authsvc.config.GenAuthAutoConfiguration}, not
 * by accidental component-scan overlap.
 */
@SpringBootApplication
public class GenAuthDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAuthDemoApplication.class, args);
    }
}
