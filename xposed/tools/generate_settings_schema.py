"""Generate the standalone editor schema from the original 1.23.3 Settings."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

repo = Path(__file__).resolve().parents[2]
settings_file = repo / 'integrations/app/src/main/java/app/revanced/bilibili/settings/Settings.kt'
source = settings_file.read_text(encoding='utf-8')
constants = (repo / 'integrations/app/src/main/java/app/revanced/bilibili/utils/Constants.java').read_text(encoding='utf-8')
resources = repo / 'patches/src/main/resources/bilibili'
strings = {node.attrib['name']: ''.join(node.itertext()) for node in
           ET.fromstring((resources / 'host/values/strings.xml').read_text(encoding='utf-8')).findall('string')}
titles = {}
for path in (resources / 'xml').glob('*.xml'):
    for node in ET.fromstring(path.read_text(encoding='utf-8')).iter():
        key = node.get('{http://schemas.android.com/apk/res/android}key')
        title = node.get('{http://schemas.android.com/apk/res/android}title', '')
        if key and title:
            titles[key] = strings.get(title.removeprefix('@string/'), title)
titles.update({'showing_bottom_items': '需要展示的底栏', 'debug': '调试日志',
               'default_playback_speed': '默认播放速度', 'long_press_playback_speed': '长按播放速度',
               'showing_drawer_items': '需要展示的侧栏'})
fallback_titles = json.loads(Path(__file__).with_name('setting-titles.json').read_text(encoding='utf-8'))
defaults = {'Boolean': False, 'Int': 0, 'Long': 0, 'Float': 0.0, 'String': '', 'StringSet': []}
pattern = r'@JvmField\s+val\s+(\w+)\s*=\s*(\w+)Setting\(\s*(?:key\s*=\s*)?"([^"]+)"'
matches = list(re.finditer(pattern, source))
assert len(matches) == len(re.findall(r'@JvmField\s+val\s+', source)), 'Unparsed setting declaration'
definitions = []
for i, match in enumerate(matches):
    symbol, kind, key = match.groups()
    declaration = source[match.start():matches[i + 1].start() if i + 1 < len(matches) else len(source)]
    default = defaults[kind]
    default_match = re.search(r'defValue\s*=\s*(setOf\(Constants.ALL_VALUE\)|"[^"]*"|Constants\.\w+|Color.WHITE|true|false|[\d.]+[fL]?)', declaration)
    if 'defValue' in declaration:
        assert default_match, f'Unparsed default: {symbol}'
        value = default_match.group(1)
        if value == 'setOf(Constants.ALL_VALUE)':
            default = ['_all']
        elif value.startswith('Constants.'):
            constant = value.split('.')[1]
            default = int(re.search(rf'\b{constant}\s*=\s*(\d+)', constants)[1])
        elif value == 'Color.WHITE':
            default = -1
        elif value in ('true', 'false'):
            default = value == 'true'
        elif value.startswith('"'):
            default = json.loads(value)
        elif kind == 'Float':
            default = float(value.rstrip('f'))
        else:
            default = int(value.rstrip('L'))
    dependency = re.search(r'dependency\s*=\s*(\w+)', declaration)
    groups = re.findall(r'// region Group:\s*([^\n\r]+)', source[:match.start()])
    title = titles.get(key) or strings.get('biliroaming_' + key + '_title') or fallback_titles.get(key) or symbol
    definitions.append({'symbol': symbol, 'key': key, 'type': kind, 'default': default,
                        'title': title, 'group': groups[-1] if groups else '基础设置',
                        'dependency': dependency[1] if dependency else None,
                        'needReboot': bool(re.search(r'needReboot\s*=\s*true', declaration)),
                        'ported': key in ('showing_bottom_items', 'debug', 'customize_home_tab', 'purify_game',
                            'disable_main_page_story', 'showing_drawer_items', 'purify_drawer_reddot', 'block_tips',
                            'customize_space', 'purify_splash', 'purify_live_popups', 'remove_live_mask',
                            'remove_live_watermark', 'live_no_block', 'block_up_rcmd_ads', 'block_recommend_guidance')})
    if key == 'purify_live_popups':
        definitions[-1]['portedOptions'] = ['shoppingCard', 'gotoBuy', 'follow', 'reserve', 'wish', 'banner',
                                           'plusOne', 'gift', 'task', 'playTogether', 'qoe']
assert len({item['key'] for item in definitions}) == len(definitions)
content = json.dumps(definitions, ensure_ascii=False, indent=2) + '\n'
target = repo / 'xposed/src/main/assets/settings-schema.json'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(content, encoding='utf-8', newline='\n')
assert target.read_text(encoding='utf-8') == content
print(f'Generated all {len(definitions)} settings; all have Chinese UI titles (source labels plus explicit fallback labels).')
