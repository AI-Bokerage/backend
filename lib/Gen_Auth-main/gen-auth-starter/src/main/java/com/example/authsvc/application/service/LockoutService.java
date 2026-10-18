package com.example.authsvc.application.service;

public interface LockoutService {

    void checkLockout(String email, String ip);

    void recordFailure(String email, String ip);

    void clearFailure(String email, String ip);
}
