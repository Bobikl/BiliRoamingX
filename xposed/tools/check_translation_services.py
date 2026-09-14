"""Optional synthetic connectivity check; never reads account data or real subtitles."""
import concurrent.futures
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding='utf-8')
UA = 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36'

def request(url, body=None, content_type=None, token=None):
    headers = {'User-Agent': UA}
    if urllib.parse.urlparse(url).hostname == 'translate.googleapis.com':
        headers['Referer'] = 'https://translate.google.com/'
    if content_type:
        headers['Content-Type'] = content_type
    if token:
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers), timeout=20) as response:
        return response.read(8 * 1024 * 1024).decode('utf-8')

def mix(value, pattern):
    for i in range(0, len(pattern) - 2, 3):
        symbol = pattern[i + 2]
        shift = ord(symbol) - 87 if symbol >= 'a' else int(symbol)
        part = value >> shift if pattern[i + 1] == '+' else value << shift
        value = (value + part) & 0xffffffff if pattern[i] == '+' else value ^ part
    return value

def check(provider):
    phase = 'authorization' if provider == 'microsoft' else 'element script'
    try:
        sentence = 'Hello world.'
        if provider == 'microsoft':
            token = request('https://edge.microsoft.com/translate/auth').strip()
            assert re.fullmatch(r'[A-Za-z0-9_-]+(?:\.[A-Za-z0-9_-]+){2}', token)
            phase = 'translation'
            response = json.loads(request('https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=zh-CHS',
                json.dumps([{'Text': sentence}]).encode('utf-8'), 'application/json; charset=UTF-8', token))
            result = response[0]['translations'][0]['text']
        else:
            script = request('https://translate.googleapis.com/translate_a/element.js')
            match = re.search(r"tkk='(\d+).(-?\d+)'", script)
            first, second = map(int, match.groups()) if match else (int(time.time() / 3600), 0)
            value = first
            for part in sentence.encode('utf-8'):
                value = mix(value + part, '+-a^+6')
            value = (mix(value, '+-3^+b+-f') ^ second) & 0xffffffff
            value %= 1000000
            token = f'{value}.{value ^ first}'
            phase = 'translation'
            response = json.loads(request('https://translate.googleapis.com/translate_a/t?sl=auto&tl=zh-CN&client=te_lib&tk=' + token,
                urllib.parse.urlencode({'q': sentence}).encode('utf-8'), 'application/x-www-form-urlencoded; charset=UTF-8'))
            result = response[0][0]
        assert isinstance(result, str) and result.strip() and result != sentence
        return {'provider': provider, 'status': 'passed', 'synthetic_result': result}
    except Exception as error:
        return {'provider': provider, 'status': 'failed', 'error': type(error).__name__,
                'phase': phase, 'http_status': getattr(error, 'code', None)}

with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(check, ['microsoft', 'google']))
content = json.dumps({'scope': 'synthetic provider connectivity; not Android runtime validation', 'results': results}, ensure_ascii=False, indent=2) + '\n'
path = Path(__file__).resolve().parents[2] / 'docs/player-translation-check-v5.json'
path.write_text(content, encoding='utf-8')
assert path.read_text(encoding='utf-8') == content
print(content)
