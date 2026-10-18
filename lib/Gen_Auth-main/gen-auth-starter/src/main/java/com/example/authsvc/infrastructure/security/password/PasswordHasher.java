package com.example.authsvc.infrastructure.security.password;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public String hash(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    public boolean verify(CharSequence rawPassword, String encodedHash) {
        long start = System.currentTimeMillis();
        boolean result = passwordEncoder.matches(rawPassword, encodedHash);
        log.info("perf.argon2.verify.ms={}", System.currentTimeMillis() - start);
        return result;
    }
}
