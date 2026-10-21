#!/usr/bin/env bash
# Run on a fresh Debian 12 / Ubuntu 24.04 VPS as root, from the server directory.
set -euo pipefail
umask 077
if [ "$(id -u)" != 0 ]; then
  echo 'Запустите установку от root: sudo bash tools/install.sh ваш-домен'
  exit 1
fi
domain=${1:-}
if [[ ! "$domain" =~ ^([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}$ ]]; then
  echo 'Укажите DNS-имя сервера, например reply.example.org. A-запись должна указывать на VPS.'
  exit 1
fi
src_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
if [ ! -f "$src_dir/replyai/web.py" ]; then
  echo 'Не найден исходный код сервера.'
  exit 1
fi
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv caddy ca-certificates tzdata
id replyai >/dev/null 2>&1 || useradd --system --home-dir /var/lib/replyai --shell /usr/sbin/nologin replyai
systemctl stop replyai.service 2>/dev/null || true
install -d -m 0755 /opt/replyai
install -d -o replyai -g replyai -m 0700 /var/lib/replyai
install -d -o root -g replyai -m 0750 /etc/replyai
# Deploy source only. Data and keys remain in separate persistent directories on upgrades.
if [ "$src_dir" != /opt/replyai ]; then
  rm -rf /opt/replyai/replyai
  cp -R "$src_dir/replyai" /opt/replyai/
  cp "$src_dir/requirements.txt" /opt/replyai/
  [ -f "$src_dir/requirements-media.txt" ] && cp "$src_dir/requirements-media.txt" /opt/replyai/
  cp -R "$src_dir/certs" /opt/replyai/
fi
python3 -m venv /opt/replyai/venv
/opt/replyai/venv/bin/python -m pip install --disable-pip-version-check -r /opt/replyai/requirements.txt
# Photo/PDF support (pypdf, pillow) is optional: a failure here must not block the upgrade.
# The server still starts; the «Фото и документы» screen reports the missing library.
if [ -f /opt/replyai/requirements-media.txt ]; then
  if /opt/replyai/venv/bin/python -m pip install --disable-pip-version-check -r /opt/replyai/requirements-media.txt; then
    echo 'Поддержка фото и документов установлена.'
  else
    echo 'ВНИМАНИЕ: не удалось установить pypdf/pillow (нет связи с PyPI?). Сервер обновлён, но распознавание фото и чтение PDF будут недоступны. Повторите установку позже.'
  fi
fi
if [ ! -f /etc/replyai/server.env ]; then
  /opt/replyai/venv/bin/python - <<'PY'
import os, secrets
from pathlib import Path
from cryptography.fernet import Fernet
text = 'REPLYAI_ADMIN_TOKEN=' + secrets.token_urlsafe(36) + '\n'
text += 'REPLYAI_MASTER_KEY=' + Fernet.generate_key().decode() + '\n'
text += 'REPLYAI_DATA_DIR=/var/lib/replyai\nREPLYAI_HOST=127.0.0.1\nPORT=8000\n'
Path('/etc/replyai/server.env').write_text(text)
os.chmod('/etc/replyai/server.env', 0o640)
PY
fi
chown root:replyai /etc/replyai/server.env
chmod 0640 /etc/replyai/server.env
chmod -R go+rX /opt/replyai
install -m 0644 "$src_dir/deploy/replyai.service" /etc/systemd/system/replyai.service
# Do not overwrite an existing web server configuration on a shared VPS.
if [ -f /etc/caddy/Caddyfile ] && ! grep -q 'Reply AI\|Zamolvi\|root \* /usr/share/caddy' /etc/caddy/Caddyfile; then
  echo 'У Caddy уже есть конфигурация. Добавьте reverse_proxy 127.0.0.1:8000 вручную.'
  echo 'Настройки приложения сохранены. Проверка и запуск Caddy оставлены администратору.'
  exit 1
fi
if [ -f /etc/caddy/Caddyfile ]; then
  cp -p /etc/caddy/Caddyfile "/etc/caddy/Caddyfile.backup.$(date +%s)"
fi
cat > /etc/caddy/Caddyfile <<EOF
# Zamolvi — no request access logs
$domain {
    reverse_proxy 127.0.0.1:8000
}
EOF
chmod 0644 /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
systemctl daemon-reload
systemctl enable --now replyai.service
systemctl enable --now caddy.service
systemctl reload caddy.service
echo
echo "Адрес для приложения: https://$domain"
if [ "${REPLYAI_QUIET_SECRETS:-0}" != 1 ]; then
  echo 'Ключ управления (сохраните у себя, не отправляйте в чаты):'
  sed -n 's/^REPLYAI_ADMIN_TOKEN=//p' /etc/replyai/server.env
fi
echo 'Откройте входящие TCP 80 и 443 в панели VPS. Порт 8000 публично не открывайте.'
echo 'Проверка: systemctl status replyai --no-pager'
