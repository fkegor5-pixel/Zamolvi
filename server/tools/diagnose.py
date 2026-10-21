#!/usr/bin/env python3
"""Owner-invoked, stdlib-only installation summary. Never return raw log lines."""
import datetime as dt
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

STAGES = {'MISSING', 'QUEUED', 'INSTALLING', 'CHECKING', 'HTTPS', 'DONE', 'HTTPS_PENDING', 'FAILED', 'BUSY'}
SIGNALS = {
    'DNS_RESOLUTION_FAILED': ('temporary failure in name resolution', 'could not resolve', 'no such host', 'nxdomain'),
    'TLS_CERTIFICATE_ERROR': ('certificate verify failed', 'tls handshake', 'acme: error', 'challenge failed'),
    'DISK_FULL': ('no space left on device', 'disk quota exceeded'),
    'PIP_PACKAGE_UNAVAILABLE': ('no matching distribution found', 'could not find a version that satisfies'),
    'SERVICE_FAILED': ('failed to start', 'failed with result', 'main process exited'),
    'OUT_OF_MEMORY': ('out of memory', 'oom-kill', 'memory cgroup out of memory'),
    'NETWORK_TIMEOUT': ('timed out', 'timeout was reached', 'connection timeout'),
    'PACKAGE_DOWNLOAD_FAILED': ('failed to fetch', 'unable to fetch some archives'),
}


def scan(text):
    text = text.lower()
    return [code for code, needles in SIGNALS.items() if any(needle in text for needle in needles)]


def command(args):
    try:
        result = subprocess.run(args, capture_output=True, timeout=5, check=False)
        return result.stdout[:131072].decode('utf-8', errors='replace')
    except (OSError, subprocess.TimeoutExpired):
        return ''


def service_summary(text):
    fields = dict(line.split('=', 1) for line in text.splitlines() if '=' in line)
    def number(key, maximum):
        value = fields.get(key, '')
        return min(int(value), maximum) if value.isdecimal() and len(value) < 15 else 0
    active = fields.get('ActiveState', '')
    sub = fields.get('SubState', '')
    return {
        'active': active if active in {'active', 'inactive', 'failed', 'activating', 'deactivating'} else 'unknown',
        'sub': sub if sub in {'running', 'dead', 'exited', 'failed', 'start', 'start-pre', 'start-post', 'auto-restart', 'stop'} else 'unknown',
        'exit_status': number('ExecMainStatus', 255), 'restarts': number('NRestarts', 2**31 - 1),
    }


def collect(directory):
    path = Path(directory)
    if not re.fullmatch(r'/root/replyai-install-[a-f0-9]{32}', str(path)):
        raise ValueError('Invalid installation directory')
    stage = 'MISSING'
    try:
        value = (path / 'status').read_text().strip()
        stage = value if value in STAGES else 'MISSING'
    except OSError:
        pass
    reasons = set()
    try:
        with (path / 'install.log').open('rb') as stream:
            stream.seek(0, 2)
            stream.seek(max(0, stream.tell() - 131072))
            reasons.update(scan(stream.read(131072).decode('utf-8', errors='replace')))
    except OSError:
        pass
    services = {}
    for name in ('replyai', 'caddy'):
        services[name] = service_summary(command(['systemctl', 'show', name, '--no-pager',
            '--property=ActiveState,SubState,ExecMainStatus,NRestarts']))
        reasons.update(scan(command(['journalctl', '-u', name, '-n', '80', '--no-pager', '--output=cat'])))
    return {
        'schema': 'replyai.install-diagnostics.v1',
        'generated_at': dt.datetime.now(dt.timezone.utc).isoformat(timespec='seconds'),
        'stage': stage, 'disk_free_bytes': shutil.disk_usage('/').free,
        'reasons': sorted(reasons), 'services': services,
    }


if __name__ == '__main__':
    print(json.dumps(collect(sys.argv[1]), separators=(',', ':')))
