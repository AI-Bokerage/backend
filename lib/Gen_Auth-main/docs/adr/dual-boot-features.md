# Infrastructure Dual-Boot Capabilities

**File:** `infrastructure/security/config/JwtSignerConfig.java` & `infrastructure/email/config/EmailProviderConfig.java`

To accommodate varying cloud and local runtime environments, `auth-svc` supports **dual-boot infrastructure backends** for its two most critical external dependencies:
1. **JWT Signing Backend**: Local PEM files vs. AWS KMS (Key Management Service).
2. **Mail Delivery Backend**: Standard SMTP vs. AWS SES (Simple Email Service) SDK v2.

The active backend for each component is selected dynamically at application startup based on environment variable configuration.

---

## 1. JWT Signing Mode (`local` vs. `kms`)

Controls how asymmetric RS256 token signatures are generated and validated.

```
                  ┌──────────────────────────────────────────────┐
                  │              JwtSigner (Interface)           │
                  └──────────────────────────────────────────────┘
                                  ▲              ▲
                                  │              │
      jwt.signing-mode=local (default)           jwt.signing-mode=kms
                                  │              │
           ┌──────────────────────────────┐     ┌──────────────────────────────┐
           │      LocalPemJwtSigner       │     │       AwsKmsJwtSigner        │
           ├──────────────────────────────┤     ├──────────────────────────────┤
           │ Signs using in-memory RSA    │     │ Delegates cryptographic      │
           │ private key extracted from   │     │ signing operations to AWS    │
           │ local PEM file.              │     │ KMS via KMS client.          │
           └──────────────────────────────┘     └──────────────────────────────┘
```

### Configuration Options
Configured in `application.yaml` via the `jwt.signing-mode` property:

- **`local` (Default)**:
  - **Class**: `LocalPemJwtSigner`
  - **Behavior**: Reads RSA key pairs directly from local PEM files or environment-provided string configs at boot time. Cryptographic calculations happen locally within the JVM process.
  - **Ideal for**: Local development, container testing, or non-AWS bare-metal deploys.
- **`kms`**:
  - **Class**: `AwsKmsJwtSigner` + `KmsClient`
  - **Behavior**: Delegates the private-key signing operation to AWS KMS via the AWS SDK. The private key never enters the memory of the `auth-svc` container, providing maximum protection against memory-dump attacks.
  - **AWS Credentials Order**:
    1. Static properties (`aws.access-key-id` & `aws.secret-access-key`) if supplied.
    2. Default credentials provider chain (IAM instance profiles, ECS tasks, or local environment variables).

---

## 2. Mail Delivery Backend (`smtp` vs. `ses`)

Controls how transactional emails (magic link resets, Super Admin bootstrap credentials) are delivered.

```
                  ┌──────────────────────────────────────────────┐
                  │             EmailProvider (Interface)        │
                  └──────────────────────────────────────────────┘
                                  ▲              ▲
                                  │              │
      mail.provider=smtp (default)                mail.provider=ses
                                  │              │
           ┌──────────────────────────────┐     ┌──────────────────────────────┐
           │      SmtpEmailProvider       │     │       SesEmailProvider       │
           ├──────────────────────────────┤     ├──────────────────────────────┤
           │ Delivers email via standard  │     │ Delivers email via AWS SES   │
           │ SMTP server using Spring's   │     │ v2 SDK client.               │
           │ JavaMailSender.              │     │                              │
           └──────────────────────────────┘     └──────────────────────────────┘
```

### Configuration Options
Configured in `application.yaml` via the `mail.provider` property:

- **`smtp` (Default)**:
  - **Class**: `SmtpEmailProvider`
  - **Behavior**: Connects to the standard SMTP server defined in `spring.mail.*` settings.
  - **Ideal for**: Local testing with tools like Mailpit, or deploying on standard hosting providers.
- **`ses`**:
  - **Class**: `SesEmailProvider` + `SesClient`
  - **Behavior**: Directly invokes the AWS SES API using the SDK client. Does not require SMTP credentials.
  - **AWS Credentials Order**: Same as the KMS client. It resolves credentials either statically or automatically through the default AWS SDK chain.

---

## 3. High-Availability & Async Offloading

Regardless of the active dual-boot backends:
- All email dispatching is entirely decoupled from the client response path. It executes inside the `authAsync` virtual-thread pool using the `@Async("authAsync")` boundary in `ProviderBackedEmailService`.
- If a mail delivery backend (either SMTP or SES) fails or throws an exception, the failure is logged and the exception is caught. It **never propagates** to the caller. The client still receives a clean `202 Accepted` response, protecting response latency and maintaining a uniform signature timing to prevent timing attacks.

---

## Structured log keys emitted

Depending on the active configuration:

| Key | Level | Source Class | Trigger |
|---|---|---|---|
| `jwt.signer.initialized mode=kms` | INFO | `JwtSignerConfig` | Booted using KMS JWT backend |
| `jwt.signer.initialized mode=local` | INFO | `JwtSignerConfig` | Booted using Local PEM JWT backend |
| `kms.client.initialized` | INFO | `JwtSignerConfig` | KMS SDK client built with credentials type |
| `email.provider.ses initialized` | INFO | `EmailProviderConfig` | Booted using SES email backend |
| `email.provider.smtp initialized` | INFO | `EmailProviderConfig` | Booted using SMTP email backend |
| `email.send.started` | INFO | `SesEmailProvider` / `SmtpEmailProvider` | Transactional email delivery started |
| `email.send.success` | INFO | `SesEmailProvider` / `SmtpEmailProvider` | Transactional email successfully sent |
| `email.send.failure` | ERROR | `SesEmailProvider` / `SmtpEmailProvider` | Delivery error caught and logged |
