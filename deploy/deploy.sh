#!/usr/bin/env bash
# Docker-host deployments only (README.md): runs ON the server, by hand or from CI over SSH:
#   printf '%s' "$REGISTRY_TOKEN" | ssh deploy@host /opt/crm/deploy.sh <backend|frontend|ai> <image-tag> <registry-user>
# Pulls that repo's images at the given tag, migrates (backend), restarts only those services,
# and waits until they answer. The registry token arrives on stdin, never on the command line.
set -euo pipefail
cd "$(dirname "$0")"

component=${1:?component: backend, frontend or ai}
tag=${2:?image tag}
user=${3:?github user}

case "$component" in
  backend)  var=BACKEND_TAG;  services=(app modauth tenant) ;;
  frontend) var=FRONTEND_TAG; services=(web) ;;
  ai)       var=AI_TAG;       services=(gateway voice docread notes email assistant search) ;;
  *) echo "unknown component: $component" >&2; exit 2 ;;
esac

docker login ghcr.io -u "$user" --password-stdin >/dev/null

# Record the new tag in .env (compose reads it for ${BACKEND_TAG} etc.).
previous=$(grep -E "^${var}=" .env | cut -d= -f2- || true)
if grep -qE "^${var}=" .env; then
  sed -i "s|^${var}=.*|${var}=${tag}|" .env
else
  echo "${var}=${tag}" >> .env
fi

rollback() {
  echo "Deploy of $component $tag failed — putting back ${previous:-nothing}" >&2
  if [ -n "$previous" ]; then
    sed -i "s|^${var}=.*|${var}=${previous}|" .env
    docker compose up -d "${services[@]}" || true
  fi
  exit 1
}
trap rollback ERR

docker compose pull "${services[@]}"
# The shared infrastructure: started on the first deploy, updated when compose.yml changes it
# (unchanged containers are left running).
docker compose up -d postgres redis rabbitmq clamav caddy

if [ "$component" = backend ]; then
  # Database changes first, from the new image, before any new code serves a request.
  docker compose run --rm --no-deps app node_modules/.bin/prisma migrate deploy
fi

docker compose up -d "${services[@]}"

# Wait until the new containers answer (AI services migrate on start, so give them time).
check() {
  case "$component" in
    backend)  docker compose exec -T app node -e "fetch('http://localhost:4000/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))" ;;
    frontend) docker compose exec -T web node -e "fetch('http://localhost:3000/').then(r=>process.exit(r.status<500?0:1)).catch(()=>process.exit(1))" ;;
    ai)       docker compose exec -T gateway python -c "import urllib.request as u; u.urlopen('http://127.0.0.1:4201/health/ready', timeout=3)" ;;
  esac
}
for _ in $(seq 60); do
  if check >/dev/null 2>&1; then
    trap - ERR
    docker image prune -f >/dev/null
    echo "Deployed $component $tag"
    exit 0
  fi
  sleep 5
done
echo "$component $tag did not become healthy within 5 minutes" >&2
docker compose logs --tail 50 "${services[@]}" >&2 || true
rollback
