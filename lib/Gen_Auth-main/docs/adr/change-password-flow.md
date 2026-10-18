# POST `/api/v1/auth/change-password`

**File:** `api/controller/AuthController.java` → `application/impl/ChangePasswordServiceImpl.java`

Allows an authenticated user to change their password. Upon a successful password update, all active sessions and refresh tokens associated with the user across all devices/sessions are revoked—excluding the current active session, which remains active to prevent sudden session termination for the active user.

---

## Sequence diagram

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant SF as JwtAuthFilter
    participant AC as AuthController
    participant CS as ChangePasswordServiceImpl
    participant PH as PasswordHasher
    participant UR as AuthUserJpaRepository
    participant SR as AuthSessionJpaRepository
    participant RR as RefreshTokenJpaRepository
    participant RS as RefreshTokenStore (Redis)
    participant AP as AuthEventPublisher
    participant AL as AuditLogService

    C->>SF: POST /api/v1/auth/change-password
    Note over C,SF: Cookie: access_token (JWT)
    alt Unauthenticated or invalid JWT
        SF-->>C: 401 Unauthorized
    end
    SF->>SF: Build AuthenticatedUser principal from JWT claims
    SF->>AC: forward to changePassword(principal, request)
    AC->>CS: changePassword(principal, request)
    
    CS->>UR: findByIdAndActiveTrue(userId)
    alt User not found or inactive
        CS-->>AC: throw InvalidCredentialsException
        AC-->>C: 401 Unauthorized
    end
    
    CS->>PH: verify(request.currentPassword, user.passwordHash)
    alt Current password verification failed
        CS-->>AC: throw InvalidCredentialsException
        AC-->>C: 401 Unauthorized
    end
    
    Note over CS: Validate newPassword != currentPassword
    CS->>PH: hash(request.newPassword)
    CS->>UR: save(user) with updated password hash
    
    Note over CS: Family-wide Revocation (Excluding Current Session)
    CS->>RR: findActiveFamilyIdsByUserIdExcludingSession(userId, currentSessionId)
    CS->>SR: deactivateAllByUserIdExceptSession(userId, currentSessionId, now)
    CS->>RR: revokeAllByUserIdExceptSession(userId, currentSessionId, now)
    loop For each other familyId
        CS->>RS: revokeByFamilyId(familyId)
    end
    
    Note over CS: Publish Lifecycle Event & Audit Log
    CS->>AP: publishPasswordChanged(userId, currentSessionId, now)
    CS->>AL: log(PASSWORD_CHANGED audit event)
    
    CS-->>AC: return void
    AC-->>C: 204 No Content
```

---

## Step-by-step breakdown

### Step 1 — Current password verification
**Where:** `ChangePasswordServiceImpl.changePassword()`

```java
AuthUserEntity user = userRepo.findByIdAndActiveTrue(principal.getUserId())
        .orElseThrow(InvalidCredentialsException::new);

if (!passwordHasher.verify(request.currentPassword(), user.getPasswordHash())) {
    throw new InvalidCredentialsException();
}
```

**Why verify the current password first?**
Ensures that a hijacked access token cannot be used to lock a user out of their own account by changing the password. It forces proof of possession of the original credentials.

---

### Step 2 — Validation of password uniqueness
**Where:** `ChangePasswordServiceImpl.changePassword()`

```java
if (passwordHasher.verify(request.newPassword(), user.getPasswordHash())) {
    throw new PasswordSameAsOldException();
}
```

**Why enforce different passwords?**
Enforcing new password uniqueness reduces the risk of password reuse and ensures users actually rotate their secrets during password reset or change events.

---

### Step 3 — Family-wide revocation (excluding current session)
**Where:** `ChangePasswordServiceImpl.changePassword()`

```java
UUID currentSessionId = principal.getSessionId();
Instant now = Instant.now();

// 1. Fetch other active refresh token families for this user
List<UUID> otherFamilyIds = refreshTokenRepo.findActiveFamilyIdsByUserIdExcludingSession(user.getId(), currentSessionId);

// 2. Deactivate other sessions and revoke other refresh tokens in Postgres
sessionRepo.deactivateAllByUserIdExceptSession(user.getId(), currentSessionId, now);
refreshTokenRepo.revokeAllByUserIdExceptSession(user.getId(), currentSessionId, now);

// 3. Purge other active families from Redis authoritative store
otherFamilyIds.forEach(refreshTokenStore::revokeByFamilyId);
```

**Why keep the current session active?**
A user changing their password shouldn't be immediately kicked out of their current session. The family-wide revocation target-kills all *other* active devices (e.g., public terminals or stolen tokens) while ensuring a smooth, uninterrupted transition for the user who initiated the change.

---

### Step 4 — Events and Auditing
**Where:** `ChangePasswordServiceImpl.changePassword()`

```java
authEventPublisher.publishPasswordChanged(user.getId(), currentSessionId, now);

auditLogService.log(new AuditLogRequest(
        user.getTenantId(),
        user.getId(),
        "PASSWORD_CHANGED",
        null, // IP is handled by caller/filters where appropriate
        null,
        "User successfully changed their password"
));
```

**Why notify the rest of the ecosystem?**
Changing a password is a critical security lifecycle event. Publishing a `auth.password.changed` event to the `cpms.events` exchange enables downstream microservices (such as the notification service to send a confirmation email) to react immediately.

---

## Error paths

| Condition | Exception | HTTP Status |
|---|---|---|
| Current password verification fails | `InvalidCredentialsException` | 401 Unauthorized |
| New password is the same as current | `PasswordSameAsOldException` | 400 Bad Request |
| Password confirmation mismatch | Bean Validation (`@PasswordsMatch`) | 400 Bad Request |
| Strong password criteria fail | Bean Validation (`@ValidPassword`) | 400 Bad Request |

---

## Structured log keys emitted

| Key | Level | When |
|---|---|---|
| `password.change.started` | INFO | Entry |
| `password.change.invalid_current` | WARN | Wrong current password |
| `password.change.same_as_old` | WARN | New password matches old |
| `password.change.revoking_others` | INFO | Purging other sessions |
| `password.change.success` | INFO | Password changed successfully |
