#!/usr/bin/env bash
# One-time (idempotent) setup for the leaguelift prod droplet — Ubuntu 24.04 LTS.
# DESIGN-DOC.md section 21 / ADR-008: single droplet, self-hosted Postgres, Caddy for
# TLS termination, deployed by GitHub Actions over SSH (.github/workflows/deploy.yml).
#
# Run this ONCE, as root, either via the DigitalOcean web console (Droplet -> Access ->
# Launch Droplet Console — no local SSH client needed) or over SSH if you already have
# access:
#
#   curl -fsSL https://raw.githubusercontent.com/ekrusznis/leaguelift/main/infra/digitalocean/bootstrap-droplet.sh | bash
#
# or copy the file over and run `bash bootstrap-droplet.sh`.
#
# Safe to re-run — every step below no-ops if already done.

set -euo pipefail

DEPLOY_USER="leaguelift"
APP_DIR="/opt/leaguelift"

echo "==> Updating apt and installing base packages"
apt-get update -y
apt-get install -y ca-certificates curl gnupg ufw fail2ban

echo "==> Installing Docker Engine + Compose plugin (official Docker repo, not Ubuntu's)"
if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo \
    "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
    $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "Docker already installed, skipping."
fi

echo "==> Creating deploy user '${DEPLOY_USER}' (no login shell needed for anything but SSH+docker)"
if ! id -u "${DEPLOY_USER}" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
  usermod -aG docker "${DEPLOY_USER}"
  mkdir -p "/home/${DEPLOY_USER}/.ssh"
  # Copy root's authorized_keys (the GitHub Actions deploy key you added) so the same
  # key can SSH in as this lower-privilege user instead of root.
  if [ -f /root/.ssh/authorized_keys ]; then
    cp /root/.ssh/authorized_keys "/home/${DEPLOY_USER}/.ssh/authorized_keys"
  fi
  chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "/home/${DEPLOY_USER}/.ssh"
  chmod 700 "/home/${DEPLOY_USER}/.ssh"
  chmod 600 "/home/${DEPLOY_USER}/.ssh/authorized_keys" || true
else
  echo "User ${DEPLOY_USER} already exists, skipping."
fi

echo "==> Creating ${APP_DIR}"
mkdir -p "${APP_DIR}/caddy_data" "${APP_DIR}/caddy_config" "${APP_DIR}/backups"
chown -R "${DEPLOY_USER}:${DEPLOY_USER}" "${APP_DIR}"

echo "==> Configuring UFW firewall (SSH + HTTP/HTTPS only)"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> Enabling fail2ban (SSH brute-force protection)"
systemctl enable --now fail2ban

echo "==> Done."
echo "Next steps:"
echo "  1. Copy docker-compose.prod.yml, Caddyfile, and .env (from .env.prod.example,"
echo "     real secret values filled in) into ${APP_DIR} as the ${DEPLOY_USER} user."
echo "  2. Point leaguelift.io and api.leaguelift.io A records at this droplet's IP."
echo "  3. Trigger the 'deploy' GitHub Actions workflow (or push to main once branch"
echo "     protection + the workflow are both live)."
