package com.example.gendemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-adm-starter auto-configures correctly
 * from outside its own package — this class lives in
 * {@code com.example.gendemo}, not {@code com.example.admsvc}, specifically
 * so the starter's beans can only be found via
 * {@link com.example.admsvc.config.GenAdmAutoConfiguration}, not by
 * accidental component-scan overlap.
 */
@SpringBootApplication
public class GenAdmDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAdmDemoApplication.class, args);
    }
}
