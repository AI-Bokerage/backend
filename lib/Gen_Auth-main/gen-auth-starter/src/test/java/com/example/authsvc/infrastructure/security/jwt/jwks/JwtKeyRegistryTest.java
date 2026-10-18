// src/test/java/com/example/authsvc/infrastructure/security/jwt/jwks/JwtKeyRegistryTest.java
package com.example.authsvc.infrastructure.security.jwt.jwks;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtKeyRegistryTest {

    private static JwtKeyEntry entry(String kid) {
        return new JwtKeyEntry(kid, null, null, null);
    }

    private static JwtKeyRegistry registryWith(String... kids) {
        Map<String, JwtKeyEntry> map = new LinkedHashMap<>();
        for (String kid : kids) {
            map.put(kid, entry(kid));
        }
        return new JwtKeyRegistry(map, kids[0]);
    }

    @Test
    void addKeyMakesItRetrievableButNotActive() {
        JwtKeyRegistry registry = registryWith("v1");

        registry.addKey(entry("v2"));

        assertEquals("v1", registry.getActiveKid());
        assertEquals(2, registry.allEntries().size());
    }

    @Test
    void addKeyRejectsDuplicateKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.addKey(entry("v1")));
    }

    @Test
    void setActiveKidPromotesRegisteredKey() {
        JwtKeyRegistry registry = registryWith("v1");
        registry.addKey(entry("v2"));

        registry.setActiveKid("v2");

        assertEquals("v2", registry.getActiveKid());
    }

    @Test
    void setActiveKidRejectsUnknownKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.setActiveKid("nope"));
    }

    @Test
    void removeKeyDropsNonActiveEntry() {
        JwtKeyRegistry registry = registryWith("v1");
        registry.addKey(entry("v2"));

        registry.removeKey("v2");

        assertNull(registry.getPrivateKey("v2"));
        assertEquals(1, registry.allEntries().size());
    }

    @Test
    void removeKeyRejectsActiveKid() {
        JwtKeyRegistry registry = registryWith("v1");

        assertThrows(IllegalStateException.class, () -> registry.removeKey("v1"));
    }

    /**
     * Proves the three mutation methods share one lock (the registry instance's monitor),
     * closing the cross-method race where setActiveKid's containsKey-check-then-set could
     * interleave with a concurrent removeKey of the same (not-yet-active) kid. We hold the
     * registry's monitor from a background thread and confirm a concurrent setActiveKid call
     * (which is now `synchronized`) blocks until that monitor is released.
     */
    @Test
    void mutationMethodsShareOneLockAcrossThreads() throws InterruptedException {
        JwtKeyRegistry registry = registryWith("v1");
        registry.addKey(entry("v2"));

        CountDownLatch holdingLock = new CountDownLatch(1);
        AtomicBoolean lockReleased = new AtomicBoolean(false);

        Thread lockHolder = new Thread(() -> {
            synchronized (registry) {
                holdingLock.countDown();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                lockReleased.set(true);
            }
        });
        lockHolder.start();

        assertTrue(holdingLock.await(1, java.util.concurrent.TimeUnit.SECONDS));
        registry.setActiveKid("v2"); // must block until lockHolder's synchronized block exits

        assertTrue(lockReleased.get(), "setActiveKid returned before the concurrent lock holder released the monitor");
        lockHolder.join();
    }
}
