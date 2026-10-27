#!/usr/bin/env bash
# Обновляет код Zamolvi на VPS из GitHub (ветка main) и проверяет, что сервер и свой бот работают.
# Если новый код не запускается, возвращает прежний. Запуск: bash /root/update-zamolvi.sh
set -uo pipefail
REPO=fkegor5-pixel/Zamolvi
CODE=/opt/replyai/replyai
PY=/opt/replyai/venv/bin/python
SERVICE=replyai

[ "$(id -u)" = 0 ] || { echo '❌ Запустите от root'; exit 1; }
[ -f "$CODE/engine.py" ] || { echo "❌ Нет кода в $CODE"; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
echo '⬇️  Скачиваю свежий код с GitHub…'
if ! curl -fsSL "https://codeload.github.com/$REPO/tar.gz/refs/heads/main" | tar -xz -C "$work"; then
  echo '❌ Не удалось скачать код. Ничего не изменено.'; exit 1
fi
new=$(echo "$work"/*/server/replyai)
"$PY" -m py_compile "$new"/*.py || { echo '❌ В новом коде ошибка. Ничего не изменено.'; exit 1; }

backup=/root/zamolvi-backup-$(date +%Y%m%d-%H%M%S)
mkdir -p "$backup" && cp -p "$CODE"/*.py "$backup"/
changed=0
for file in "$new"/*.py; do
  name=$(basename "$file")
  if ! cmp -s "$file" "$CODE/$name"; then
    echo "   обновлён $name"; changed=$((changed + 1))
  fi
  cp "$file" "$CODE/$name"
done
chmod -R go+rX "$CODE"
echo "📦 Обновлено файлов: $changed (копия прежних: $backup)"

"$PY" -c 'import PIL' 2>/dev/null || { echo '📦 Ставлю Pillow для картинок…'; "$PY" -m pip install -q pillow; }
"$PY" -m pip install -q --disable-pip-version-check -r "$work"/*/server/requirements.txt

started=$(date '+%Y-%m-%d %H:%M:%S')
systemctl restart "$SERVICE"
sleep 10
if systemctl is-active --quiet "$SERVICE" && curl -fsS -m 5 http://127.0.0.1:8000/healthz >/dev/null; then
  echo '✅ Сервер работает'
else
  echo '❌ Сервер не запустился. Возвращаю прежний код…'
  journalctl -u "$SERVICE" --since "$started" --no-pager | tail -n 20
  cp -p "$backup"/*.py "$CODE"/
  systemctl restart "$SERVICE"; sleep 5
  systemctl is-active --quiet "$SERVICE" && echo '↩️  Прежний код снова работает.' || echo '❌ Сервер не работает и на прежнем коде.'
  exit 1
fi

echo '🤖 Свой бот (ждём до 6 минут: он запускается при плановой проверке)…'
for _ in $(seq 36); do
  line=$(journalctl -u "$SERVICE" --since "$started" --no-pager | grep -E 'Свой бот:' | tail -n 1)
  [ -n "$line" ] && break
  sleep 10
done
if [ -z "$line" ]; then
  echo '⚠️  От бота нет сообщений. Проверьте, что в «Избранном» есть ссылка на бота, или напишите туда «создать бота».'
elif echo "$line" | grep -q 'запущен'; then
  echo "✅ ${line#*Свой бот: }"
else
  echo "⚠️  ${line#*Свой бот: }"
fi
errors=$(journalctl -u "$SERVICE" --since "$started" --no-pager | grep -E 'Свой бот: ошибка|Traceback' | tail -n 5)
[ -n "$errors" ] && { echo '❗ Ошибки:'; echo "$errors"; }
exit 0
