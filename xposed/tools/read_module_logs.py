"""Read only the scoped module's LSPosed diagnostics from an explicitly authorized device."""
import argparse
import datetime
import json
import subprocess
import sys
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--adb', type=Path, required=True)
p.add_argument('--serial', required=True)
p.add_argument('--report', type=Path, required=True)
a = p.parse_args()
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
base = [str(a.adb), '-s', a.serial]


def adb(*args):
    return subprocess.run(base + list(args), capture_output=True, check=True, encoding='utf-8').stdout


names = adb('shell', "su -c 'ls /data/adb/lspd/log'").splitlines()
results = {}
for name in names:
    if not (name.startswith(('modules_', 'verbose_')) and name.endswith('.log')):
        continue
    if any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.:-' for c in name):
        raise ValueError('Unexpected log filename')
    lines = adb('shell', "su -c 'cat /data/adb/lspd/log/" + name + "'").splitlines()
    scoped = []
    include = False
    for line in lines:
        if line.startswith('[ '):
            include = '[app.revanced.bilibili.xposed,' in line
        if include:
            scoped.append(line)
    results[name] = scoped
logcat = adb('logcat', '-d', '-v', 'threadtime', '-s', 'BiliRoamingX-LSPosed:*', 'RemotePreferences:W')
crashes = adb('logcat', '-b', 'crash', '-d', '-v', 'threadtime')
report = {
    'captured_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'access': 'read-only ADB su; explicitly authorized by user',
    'module_logs': results,
    'module_logcat': logcat.splitlines(),
    'crash_buffer_related_lines': [line for line in crashes.splitlines() if any(
        token in line for token in ('tv.danmaku.bili', 'app.revanced.bilibili.xposed', 'BiliRoamingX'))],
    'limits': 'Only available log buffers; absence of errors is not proof of all paths being error-free.',
}
text = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
a.report.write_text(text, encoding='utf-8')
assert a.report.read_text(encoding='utf-8') == text
print(text)
