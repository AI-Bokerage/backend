#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 24.04 EC2 server (README.md, step 3). Run as the default
# `ubuntu` user:   curl -fsSL <raw url of this file> | sudo bash -s -- "<deploy public key>"
#            or:   scp it over, then  sudo bash server-setup.sh "<deploy public key>"
set -euo pipefail
DEPLOY_KEY=${1:?pass the GitHub deploy public key (ssh-ed25519 ...)}

# Docker Engine + compose plugin, from Docker's own apt repository.
if ! command -v docker >/dev/null; then
  apt-get update -q
  apt-get install -yq ca-certificates curl rsync
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -yq docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi
# Containers' logs rotate instead of filling the disk.
cat > /etc/docker/daemon.json <<'JSON'
{ "log-driver": "local", "log-opts": { "max-size": "20m", "max-file": "5" } }
JSON
systemctl restart docker

# 2 GB of swap: a safety net for memory peaks (ClamAV reloading its database, image pulls).
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# The user GitHub Actions logs in as: may run docker, owns /opt/crm, nothing else.
id deploy >/dev/null 2>&1 || useradd --create-home --shell /bin/bash deploy
usermod -aG docker deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
echo "$DEPLOY_KEY" > /home/deploy/.ssh/authorized_keys
chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
install -d -m 750 -o deploy -g deploy /opt/crm

echo "Done. Next: copy this server's secrets to /opt/crm (README.md, step 4)."
