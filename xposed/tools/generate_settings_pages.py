"""Preserve the original settings hierarchy, labels and option arrays as module data."""
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding='utf-8')
repo = Path(__file__).resolve().parents[2]
resources = repo / 'patches/src/main/resources/bilibili'
fragments = repo / 'integrations/app/src/main/java/app/revanced/bilibili/settings/fragments'
schema = json.loads((repo / 'xposed/src/main/assets/settings-schema.json').read_text(encoding='utf-8'))
symbols = {s['symbol']: s for s in schema}
keys = {s['key']: s for s in schema}
strings, arrays = {}, {}
for file in (resources / 'host/values').glob('*.xml'):
    for node in ET.parse(file).getroot():
        name = node.get('name')
        if node.tag == 'string':
            strings[name] = ''.join(node.itertext()).replace('\\n', '\n').replace("\\'", "'")
        elif node.tag in ('string-array', 'array'):
            arrays[name] = [''.join(n.itertext()) for n in node]
def resolve(value):
    if value.startswith('@string/'):
        return strings.get(value[8:], value)
    if value.startswith('@array/'):
        return [resolve(v) for v in arrays[value[7:]]]
    return value
mapping, custom = {}, {}
for file in fragments.glob('*.kt'):
    source = file.read_text(encoding='utf-8')
    for xml, cls in re.findall(r'@SettingFragment\("([^"]+)"\)\s*class\s+(\w+)', source):
        mapping[cls] = xml
    if ' : BaseWidgetSettingFragment()' in source:
        custom[file.stem] = source
def convert(node):
    attrs = {k.split('}')[-1]: resolve(v) for k, v in node.attrib.items()}
    kind = node.tag.split('.')[-1]
    result = {'kind': kind, **{k: attrs[k] for k in ('key', 'title', 'summary', 'dependency', 'entries', 'entryValues', 'radioEntries', 'radioEntryValues', 'radioEntrySummaries') if k in attrs}}
    if result.get('key') in ('default_speed', 'long_press_speed', 'override_speed'):
        result['key'] = {'default_speed': 'default_playback_speed', 'long_press_speed': 'long_press_playback_speed', 'override_speed': 'playback_speed_override'}[result['key']]
    if 'fragment' in attrs:
        cls = attrs['fragment'].split('.')[-1]
        result['page'] = mapping.get(cls, cls)
    result['children'] = [convert(n) for n in node if n.tag not in ('intent', 'extra')]
    return result
pages = {p.stem: convert(ET.parse(p).getroot()) for p in (resources / 'xml').glob('biliroaming*.xml')
         if ET.parse(p).getroot().tag.endswith('PreferenceScreen')}
for cls, source in custom.items():
    seen = set()
    rows = []
    for symbol in re.findall(r'Settings\.(\w+)', source):
        if symbol not in symbols or symbol in seen:
            continue
        seen.add(symbol)
        setting = symbols[symbol]
        rows.append({'kind': 'Preference', 'key': setting['key'], 'title': setting['title'], 'children': []})
    pages[cls] = {'kind': 'PreferenceScreen', 'children': rows}
pages['PlayerAccessKeys'] = {'kind': 'PreferenceScreen', 'children': [
    {'kind': 'Preference', 'key': 'access_key_main', 'title': '主站 AccessKey', 'summary': '用于主站新版播放接口。留空使用当前账号。', 'children': []},
    {'kind': 'Preference', 'key': 'access_key_th', 'title': '泰区 AccessKey', 'summary': '依赖尚未移植的泰区播放链路。', 'children': []},
]}
pages['CustomizeSubtitleStyleFragment']['children'][3:3] = [
    {'kind': 'Action', 'key': 'subtitle_font_import', 'title': '导入字幕字体', 'summary': '选择 TTF 或 OTF 文件，不超过 16MB。', 'children': []},
    {'kind': 'Action', 'key': 'subtitle_font_reset', 'title': '恢复默认字幕字体', 'children': []},
]
# Every original fragment link must resolve, including widget-based settings pages.
def walk(row):
    yield row
    for child in row.get('children', []):
        yield from walk(child)
for page in pages.values():
    for row in walk(page):
        if row.get('key') == 'custom_access_key':
            row['page'] = 'PlayerAccessKeys'
        assert 'page' not in row or row['page'] in pages, row
        scope_notes = {
            'purify_splash': '本版过滤新获取的开屏内容，已有开屏缓存暂未清理。',
            'disable_main_page_story': '本版仅移除首页左上角的竖屏视频入口。',
            'block_up_rcmd_ads': '本版只屏蔽部分视频弹幕广告，其他推荐广告仍待移植。',
            'auto_generate_subtitle': '本轮暂缓适配自动生成与翻译字幕。',
            'subtitle_translate_server': '本轮暂缓适配自动翻译。',
            'subtitle_import_save': '支持 UTF-8 的 ASS、SRT、VTT、JSON；保存为 ZIP，包含各语言的 JSON 和 SRT。',
            'custom_access_key': '本版已接入主站新版播放接口；泰区仍待地区播放链路移植。',
            'trial_vip_quality': '只处理服务器已返回的 DASH 画质，实际可用性和试用时限由服务器决定。',
        }
        if row.get('key') in scope_notes:
            row['summary'] = row.get('summary', '') + '\n' + scope_notes[row['key']]
used = {row.get('key') for page in pages.values() for row in walk(page)}
# Internal/default values absent from XML remain available in the directory without editable controls.
missing = [s for s in schema if s['key'] not in used]
pages['remaining_settings'] = {'kind': 'PreferenceScreen', 'children': [
    {'kind': 'Preference', 'key': s['key'], 'title': s['title'], 'children': []} for s in missing]}
pages['biliroaming_settings']['children'].append({'kind': 'PreferenceCategory', 'children': [
    {'kind': 'Preference', 'title': '其他配置', 'page': 'remaining_settings', 'children': []}]})
text = json.dumps(pages, ensure_ascii=False, indent=2) + '\n'
target = repo / 'xposed/src/main/assets/settings-pages.json'
target.write_text(text, encoding='utf-8', newline='\n')
assert target.read_text(encoding='utf-8') == text
assert all(s['key'] in {r.get('key') for p in pages.values() for r in walk(p)} for s in schema)
# Verify reachable pages, so an orphan XML cannot give a false coverage count.
reachable = set()
def visit(key):
    if key in reachable:
        return
    reachable.add(key)
    for row in walk(pages[key]):
        if 'page' in row:
            visit(row['page'])
visit('biliroaming_settings')
assert all(s['key'] in {r.get('key') for name in reachable for r in walk(pages[name])} for s in schema)
for name in reachable:
    for row in walk(pages[name]):
        for attr in ('title', 'summary'):
            assert not row.get(attr, '').startswith('@'), (name, row)
print(f'Generated {len(pages)} original/settings pages; covers all {len(schema)} settings ({len(missing)} extra values).')
