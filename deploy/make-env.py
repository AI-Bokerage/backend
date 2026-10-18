#!/usr/bin/env python3
"""Make every service's variables for one environment, for any hosting platform.

    python3 deploy/make-env.py --ai-repo "../ai service" --domain crm.example.com \\
        --stage staging --host-template "{name}" --out ~/crm-staging

Writes <out>/services/<service>.env — everything that service needs: fresh random passwords and
keys (matching wherever two services share one), and the private addresses of the services it
talks to. The only thing that differs between platforms is how a service reaches another:

    --host-template "{name}"                    docker compose (EC2 or any Docker host), Render
    --host-template "{name}.railway.internal"   Railway
    --host-template "{name}.internal"           Fly.io
    --host-template "{name}.<namespace>.svc"    Kubernetes

so the service names (SERVICES below) must be the same on the platform. Paste each file into the
platform's variables for that service, or use it as an env file (compose.yml does). Provider keys
(Groq, OCR.space, Jina) and email are left to fill in: search the output for CHANGE_ME.

The AI services' shared keys come from the AI repo's own generator (tools/scripts/dev_keys.py:
render + KeyStore), with a fresh key store, so e.g. the backend's AI_NOTES_KEY and notes-svc's
API_KEYS match. Nothing written here is ever committed.
"""
from __future__ import annotations

import argparse
import base64
import importlib.util
import json
import secrets
import subprocess
import sys
import tempfile
from pathlib import Path

# name → port. Infrastructure first, then the CRM, then the AI services.
SERVICES = {
    "postgres": 5432, "redis": 6379, "rabbitmq": 5672, "clamav": 3310,
    "web": 3000, "app": 4000, "modauth": 8102, "tenant": 8103,
    "gateway": 4201, "voice": 4202, "docread": 4203, "notes": 4204, "email": 4205,
    "assistant": 4206, "search": 4207,
}
AI_SERVICES = ["gateway", "voice", "docread", "notes", "email", "assistant", "search"]
JAVA_OPTS = ("-Xmx256m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m "
             "-XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m")


def rand_hex(n: int = 32) -> str:
    return secrets.token_hex(n)


def ai_env_files(ai_repo: Path) -> tuple[dict[str, str], dict[str, str]]:
    """{service: .env text} and the shared keys, from the AI repo's .env.example files."""
    spec = importlib.util.spec_from_file_location("dev_keys", ai_repo / "tools/scripts/dev_keys.py")
    dev_keys = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(dev_keys)
    with tempfile.TemporaryDirectory() as tmp:
        store = dev_keys.KeyStore(Path(tmp) / "keys.json")  # fresh: never the developer's dev keys
        envs = {
            svc: dev_keys.render((ai_repo / f"apps/{svc}-svc/.env.example").read_text(), store)
            for svc in AI_SERVICES
        }
        store.save()
        keys = json.loads((Path(tmp) / "keys.json").read_text())
    return envs, keys


def set_vars(text: str, values: dict[str, str]) -> str:
    """Set NAME=value lines (replacing existing ones, appending the rest)."""
    lines = text.splitlines()
    done = set()
    for i, line in enumerate(lines):
        name = line.split("=", 1)[0]
        if "=" in line and not line.startswith("#") and name in values:
            lines[i] = f"{name}={values[name]}"
            done.add(name)
    lines += [f"{k}={v}" for k, v in values.items() if k not in done]
    return "\n".join(lines) + "\n"


def jwt_keypair() -> tuple[bytes, bytes]:
    """A new RSA keypair for the auth service (it signs; the backend verifies)."""
    with tempfile.TemporaryDirectory() as tmp:
        priv, pub = Path(tmp) / "private.pem", Path(tmp) / "public.pem"
        subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
                        "-out", str(priv)], check=True, capture_output=True)
        subprocess.run(["openssl", "pkey", "-in", str(priv), "-pubout", "-out", str(pub)],
                       check=True, capture_output=True)
        return priv.read_bytes(), pub.read_bytes()


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ai-repo", required=True, type=Path, help="a checkout of AI-Bokerage/AI-service")
    ap.add_argument("--domain", required=True, help="the public address of the web service, e.g. crm.example.com")
    ap.add_argument("--stage", required=True, choices=["staging", "production"])
    ap.add_argument("--host-template", default="{name}",
                    help='how services reach each other, e.g. "{name}.railway.internal" (default: "{name}")')
    ap.add_argument("--out", required=True, type=Path, help="folder to create (must not exist)")
    a = ap.parse_args()
    out: Path = a.out.expanduser()
    if out.exists():
        sys.exit(f"{out} already exists — refusing to overwrite an environment's secrets")
    (out / "services").mkdir(parents=True)

    def host(name: str) -> str:
        return a.host_template.format(name=name)

    def at(name: str) -> str:
        return f"http://{host(name)}:{SERVICES[name]}"

    url = f"https://{a.domain}"
    production = a.stage == "production"
    ai_envs, k = ai_env_files(a.ai_repo)
    private_pem, public_pem = jwt_keypair()
    pg, rabbit, internal = rand_hex(24), rand_hex(24), rand_hex()
    pg_url = f"{host('postgres')}:5432"
    # Production AI services call back only over https: through the public address (which routes
    # /api to the backend). Elsewhere they call the backend directly on the private network.
    callback_base = url if production else at("app")
    callback_host = a.domain if production else host("app")

    files: dict[str, dict[str, str] | str] = {
        "postgres": {
            "POSTGRES_USER": "postgres", "POSTGRES_PASSWORD": pg, "POSTGRES_DB": "crm",
            "PGDATA": "/var/lib/postgresql/data/pgdata",  # below a mounted volume's root (lost+found)
        },
        "rabbitmq": {"RABBITMQ_DEFAULT_USER": "crm", "RABBITMQ_DEFAULT_PASS": rabbit},
        "web": {
            "PORT": "3000", "NEXT_PUBLIC_APP_NAME": "Mortgage CRM",
            # Also build arguments: the frontend's /api rewrites point here.
            "AUTH_SERVICE_URL": at("modauth"), "CRM_API_URL": at("app"),
        },
        "app": {
            "NODE_ENV": "production", "PORT": "4000", "API_PREFIX": "/api/v1", "APP_NAME": "Mortgage CRM",
            "ALLOWED_ORIGINS": url, "LOG_LEVEL": "info", "DB_CLIENT": "prisma",
            "DATABASE_URL": f"postgresql://postgres:{pg}@{pg_url}/crm",
            "MONGODB_URI": "mongodb://unused:27017/unused",  # required by config/env.ts, unused
            "JWT_ACCESS_SECRET": rand_hex(), "JWT_REFRESH_SECRET": rand_hex(), "BCRYPT_SALT_ROUNDS": "12",
            "INTERNAL_SERVICE_SECRET": internal,
            "AUTH_SERVICE_BASE_URL": at("modauth"), "TENANT_SERVICE_BASE_URL": at("tenant"),
            "AUTH_JWT_ISSUER": url, "AUTH_JWT_AUDIENCE": "modules-auth",
            "AUTH_JWT_PUBLIC_KEY": '"' + public_pem.decode().strip().replace("\n", "\\n") + '"',
            "TOKEN_ENCRYPTION_KEY": rand_hex(), "CRM_FRONTEND_URL": url,
            "CLAMAV_HOST": host("clamav"), "CLAMAV_PORT": "3310",
            "DOCUMENT_STORAGE_DIR": "/data/documents",
            "GOOGLE_CLIENT_ID": "", "GOOGLE_CLIENT_SECRET": "",
            "GOOGLE_REDIRECT_URI": f"{url}/api/v1/crm/integrations/google/callback",
            **{f"AI_{s.upper()}_URL": at(s) for s in AI_SERVICES},
            "AI_GATEWAY_KEY": k["backend"], "AI_GATEWAY_ADMIN_KEY": k["gateway.admin"],
            **{f"AI_{s.upper()}_KEY": k[f"backend.{s}"] for s in AI_SERVICES if s != "gateway"},
            "AI_DOCREAD_WEBHOOK_SECRET": k["docread.webhook"],
            "AI_DATA_API_KEY": k["crm.assistant"],
            "AI_CALLBACK_BASE_URL": callback_base, "AI_COST_OVERRIDE_LIMIT": "100",
        },
        "modauth": {
            "PORT": "8102", "JAVA_TOOL_OPTIONS": JAVA_OPTS,
            "AUTH_DB_URL": f"jdbc:postgresql://{pg_url}/auth_db",
            "AUTH_DB_USERNAME": "postgres", "AUTH_DB_PASSWORD": pg,
            "REDIS_HOST": host("redis"), "REDIS_PORT": "6379",
            "JWT_ISSUER": url, "JWT_AUDIENCE": "modules-auth",
            "JWT_KEY_ENCRYPTION_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
            # This environment's own keypair (the image's entrypoint writes them to files).
            "JWT_PRIVATE_KEY_B64": base64.b64encode(private_pem).decode(),
            "JWT_PUBLIC_KEY_B64": base64.b64encode(public_pem).decode(),
            "INTERNAL_SERVICE_SECRET": internal,
            "PLATFORM_WEBHOOK_URL": f"{at('app')}/api/v1/platform/webhooks/owner-accepted",
            "PLATFORM_EVENTS_URL": f"{at('app')}/api/v1/platform/webhooks/auth-events",
            "CRM_BASE_URL": f"{at('app')}/api/v1/crm",
            "FRONTEND_RESET_URL": f"{url}/reset-password",
            "FRONTEND_INVITE_ACCEPT_URL": f"{url}/invitations/accept",
            "SESSION_IDLE_TIMEOUT_MINUTES": "30",
            "MANAGEMENT_HEALTH_RABBIT_ENABLED": "false",  # it has no RabbitMQ; keeps /actuator/health UP
            "SUPER_ADMIN_EMAIL": "CHANGE_ME@your-company.com",
            "SUPER_ADMIN_PASSWORD": secrets.token_urlsafe(18),
            # Email for invitations and password resets (e.g. Amazon SES). Optional on a demo.
            "SMTP_HOST": "CHANGE_ME_smtp.example.com", "SMTP_PORT": "587",
            "SPRING_MAIL_USERNAME": "CHANGE_ME", "SPRING_MAIL_PASSWORD": "CHANGE_ME",
            "SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH": "true",
            "SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE": "true",
        },
        "tenant": {
            "PORT": "8103", "JAVA_TOOL_OPTIONS": JAVA_OPTS,
            "TNT_DB_URL": f"jdbc:postgresql://{pg_url}/tenant_db",
            "TNT_DB_USERNAME": "postgres", "TNT_DB_PASSWORD": pg,
            "TNT_REDIS_HOST": host("redis"), "TNT_REDIS_PORT": "6379",
            "SPRING_DATA_REDIS_DATABASE": "1",  # one Redis: modauth 0, tenant 1, email 2
            "INTERNAL_SERVICE_SECRET": internal,
        },
    }
    for svc, text in ai_envs.items():
        wiring = {
            "PORT": str(SERVICES[svc]),
            "DATABASE_URL": f"postgresql+asyncpg://postgres:{pg}@{pg_url}/broker_{svc}",
            # Production refuses mock providers and plain-http callbacks.
            "ENV": "production" if production else "development",
        }
        if svc != "gateway":
            wiring |= {"RABBITMQ_URL": f"amqp://crm:{rabbit}@{host('rabbitmq')}:5672/",
                       "GATEWAY_URL": at("gateway")}
        if "WEBHOOK_SECRET=" in text:
            wiring["WEBHOOK_ALLOWED_HOSTS"] = callback_host
        wiring |= {
            "voice": {"STORAGE_DIR": "/data/audio"},
            "docread": {"STORAGE_DIR": "/data/documents"},
            "email": {"VALKEY_URL": f"redis://{host('redis')}:6379/2"},
            "assistant": {"CRM_DATA_MODE": "http", "CRM_BASE_URL": f"{at('app')}/api/v1"},
        }.get(svc, {})
        if svc == "gateway" and production:
            # Your client's decisions, not defaults: the gateway won't start until both are true.
            wiring["OCR_EXTERNAL_PROVIDER_APPROVED"] = "CHANGE_ME_true_once_client_approves_ocr_space"
            wiring["EMBEDDING_PROVIDER_APPROVED"] = "CHANGE_ME_true_once_client_confirms_jina_canada"
        files[svc] = set_vars(text, wiring)

    for name, content in files.items():
        text = content if isinstance(content, str) else set_vars("", content)
        (out / f"services/{name}.env").write_text(text)
    # docker compose reads this for ${CRM_DOMAIN} (Caddy); deploy.sh adds the image tags.
    (out / ".env").write_text(f"CRM_DOMAIN={a.domain}\n")

    for f in out.rglob("*"):
        f.chmod(0o700 if f.is_dir() else 0o600)
    print(f"Made {out}/services/*.env. Fill every CHANGE_ME (grep -rn CHANGE_ME {out}); for AI, the "
          "provider keys in services/gateway.env (LLM, OCR, transcription, embeddings).")


if __name__ == "__main__":
    main()
