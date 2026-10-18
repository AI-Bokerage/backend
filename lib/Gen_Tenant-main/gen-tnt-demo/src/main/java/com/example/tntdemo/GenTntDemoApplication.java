// gen-tnt-demo/src/main/java/com/example/tntdemo/GenTntDemoApplication.java
package com.example.tntdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-tnt-starter auto-configures correctly
 * from outside its own package — lives in {@code com.example.tntdemo}, not
 * {@code com.example.tnt_svc}, so the starter's beans can only be found via
 * {@link com.example.tnt_svc.config.GenTntAutoConfiguration}.
 */
@SpringBootApplication
public class GenTntDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenTntDemoApplication.class, args);
    }
}
