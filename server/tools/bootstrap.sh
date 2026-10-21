#!/usr/bin/env bash
# Detached, root-only installation job started by the Android SSH client.
set -Eeuo pipefail
umask 077
domain=${1:?DNS name required}
work=${2:?Work directory required}
state() { printf '%s\n' "$1" > "$work/status.new"; mv -f "$work/status.new" "$work/status"; }
trap 'result=$?; if [ "$result" != 0 ]; then state FAILED; fi' EXIT
exec 9>"${REPLYAI_SETUP_LOCK:-/run/replyai-install.lock}"
if ! flock -n 9; then state BUSY; exit 0; fi
state INSTALLING
# Never put the admin token or master key in the installation log.
export REPLYAI_QUIET_SECRETS=1 PIP_NO_INPUT=1
bash "$work/tools/install.sh" "$domain" > "$work/install.log" 2>&1
state CHECKING
systemctl is-active --quiet replyai.service
state HTTPS
if python3 - "$domain" <<'PY'
import json, sys, time, urllib.request
url = 'https://' + sys.argv[1] + '/healthz'
for attempt in range(24):
    try:
        with urllib.request.urlopen(url, timeout=8) as response:
            if response.status == 200 and isinstance(json.loads(response.read(4096)), dict):
                sys.exit(0)
    except Exception:
        pass
    time.sleep(5)
sys.exit(1)
PY
then state DONE
else state HTTPS_PENDING
fi
