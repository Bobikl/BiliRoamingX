"""Static checks for the final module APK; never connects to a device."""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

FORBIDDEN = [
    'userBlocked', 'user_blocked_', 'user_status_last_check_time_', 'BlacklistInfo',
    'checkUserStatus', 'biliroaming_blocked_title', 'biliroaming_blocked_description',
    'biliroaming_unblocked_title', 'biliroaming_unblocked_description',
    'BiliRoamingServerBlacklistLog', '82kPqomaPXmNG1KYpemYwCxgGaViTMfWQ7oNyBh48mRC',
    'JULvAwoUgmc', 'black.qimo.ink',
]


def defined_classes(data):
    def u32(offset):
        return struct.unpack_from('<I', data, offset)[0]

    strings = []
    for i in range(u32(56)):
        offset = u32(u32(60) + i * 4)
        while data[offset] & 128:
            offset += 1
        offset += 1
        strings.append(data[offset:data.index(b'\0', offset)])
    types = [strings[u32(u32(68) + i * 4)] for i in range(u32(64))]
    return [types[u32(u32(100) + i * 32)].decode('ascii') for i in range(u32(96))]


parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--report', type=Path, required=True)
parser.add_argument('--host-only', action='store_true')
parser.add_argument('--sdk', type=Path)
args = parser.parse_args()
with zipfile.ZipFile(args.apk) as archive:
    contents = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
hits = [(name, marker) for name, data in contents.items() for marker in FORBIDDEN
        if any(marker.encode(encoding) in data for encoding in ('utf-8', 'utf-16le'))]
assert not hits, hits
entry = contents['META-INF/xposed/java_init.list'].decode('utf-8').strip()
assert entry == 'app.revanced.bilibili.xposed.ModuleEntry'
assert contents['META-INF/xposed/scope.list'].decode('utf-8').strip() == 'tv.danmaku.bili'
properties = dict(line.split('=', 1) for line in contents['META-INF/xposed/module.prop'].decode('utf-8').splitlines() if '=' in line)
assert properties['minApiVersion'] == properties['targetApiVersion'] == '102'
assert properties['staticScope'] == 'true'
assert properties['autoHotReload'] == 'false'
assert 'assets/xposed_init' not in contents
assert not any(name.endswith(('.apk', '.jar')) for name in contents), 'Nested APK/JAR payload'
classes = [name for path, data in contents.items() if path.endswith('.dex') for name in defined_classes(data)]
assert 'Lapp/revanced/bilibili/xposed/ModuleEntry;' in classes
assert not any(name.startswith('Lio/github/libxposed/api/') for name in classes), 'API must remain compileOnly'
assert not any(name.startswith('Ltv/danmaku/bili/') for name in classes), 'Host dummy classes must not be packaged'
assert not any(name.startswith('Lapp/revanced/bilibili/patches/') for name in classes), 'Entire integrations must not be packaged'
schema = json.loads(contents['assets/settings-schema.json'].decode('utf-8'))
assert len(schema) == len({item['key'] for item in schema}) == 204
assert next(item for item in schema if item['key'] == 'showing_bottom_items')['default'] == ['_all']
assert all(item['type'] in ('Boolean', 'Int', 'Long', 'Float', 'String', 'StringSet') for item in schema)
host_components = None
if args.host_only:
    assert args.sdk is not None, '--sdk is required for APK manifest verification'
    assert not any(name.startswith('Lio/github/libxposed/service/') for name in classes), 'Service library must be removed'
    dex = b''.join(data for path, data in contents.items() if path.endswith('.dex'))
    assert b'app.revanced.bilibili.xposed.catalog' not in dex, 'Obsolete provider authority remains'
    command = ['java', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8',
               '-Dcom.android.sdklib.toolsdir=' + str(args.sdk / 'cmdline-tools/latest'),
               '-classpath', str(args.sdk / 'cmdline-tools/latest/lib/apkanalyzer-classpath.jar'),
               'com.android.tools.apk.analyzer.ApkAnalyzerCli', 'manifest', 'print', str(args.apk)]
    decoded = subprocess.run(command, capture_output=True, check=True, encoding='utf-8').stdout
    manifest = ET.fromstring(decoded)
    application = manifest.find('application')
    assert application is not None
    component_tags = {'activity', 'activity-alias', 'provider', 'receiver', 'service'}
    host_components = [node.tag for node in application if node.tag in component_tags]
    assert not host_components, host_components
    assert '{http://schemas.android.com/apk/res/android}name' not in application.attrib, 'Custom module Application remains'
    assert not manifest.findall('uses-permission'), 'Host-only module should request no permissions'
report = {'apk': args.apk.name, 'bytes': args.apk.stat().st_size,
          'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
          'checks': 'passed', 'entry': entry, 'api': 102, 'scope': 'tv.danmaku.bili',
          'defined_class_count': len(classes), 'settings_count': len(schema),
          'forbidden_marker_hits': hits, 'device_testing': 'not performed; user handles phone validation'}
if args.host_only:
    report['settings_mode'] = 'host-only; legacy framework preferences read only for one-time migration'
    report['standalone_components'] = host_components
content = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
args.report.write_text(content, encoding='utf-8')
assert args.report.read_text(encoding='utf-8') == content
print(content)
