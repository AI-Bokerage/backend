package com.example.moduletenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Host application for gen-tnt-starter. Everything under this package is
 * additive — gen-tnt-starter's own {@code GenTntAutoConfiguration} supplies
 * tenant CRUD (create/get/suspend/reactivate/cancel/purge) and the
 * provisioning saga end to end; the one thing it doesn't expose is a
 * list-tenants endpoint, which {@link com.example.moduletenant.controller.TenantListController}
 * adds by querying gen-tnt-starter's own {@code TenantRepository} bean directly.
 */
@SpringBootApplication
public class TenantModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenantModuleApplication.class, args);
    }
}
