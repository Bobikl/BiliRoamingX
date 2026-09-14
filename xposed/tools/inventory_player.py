"""Collect original fingerprint strings, then inspect the untouched host DEX offline."""
from pathlib import Path
import json
import re
import subprocess
import sys
sys.stdout.reconfigure(encoding='utf-8')
sys.stderr.reconfigure(encoding='utf-8')
repo = Path(__file__).resolve().parents[2]
patches = repo / 'patches/src/main/kotlin/app/revanced/patches/bilibili'
needles = set()
for folder in ('video/player/fingerprints', 'video/quality/fingerprints', 'video/subtitle/fingerprints', 'misc/config/fingerprints', 'misc/protobuf/fingerprints', 'misc/okhttp/fingerprints', 'misc/other/fingerprints'):
    for file in (patches / folder).glob('*.kt'):
        source = file.read_text(encoding='utf-8')
        match = re.search(r'strings\s*=\s*(?:listOf|setOf)\(((?:"(?:\\.|[^"])*"|[^()])*)\)', source, re.S)
        if match:
            needles.update(re.findall(r'"([^"\n]+)"', match[1]))
needles.update(['get free data failed', 'mEnableHwCodec', 'PlayerCoreServiceV2', 'playback_speed_text_group', 'PlaySpeedManagerImpl'])
needles = {n.replace('\\$', '$') for n in needles if len(n) >= 8}
needles.add('Canceled')
build = repo / 'xposed/build/player-inventory'
build.mkdir(parents=True, exist_ok=True)
queries = build / 'queries.txt'
queries.write_text('\n'.join(sorted(needles)), encoding='utf-8')
named = ['tv.danmaku.biliplayerv2.service.IRenderContainerService', 'tv.danmaku.videoplayer.core.videoview.AspectRatio',
    'com.bilibili.playerbizcommon.gesture.GestureService', 'tv.danmaku.ijk.media.player.IjkMediaConfigParams',
    'com.bilibili.player.tangram.basic.PlaySpeedManagerImpl', 'com.bilibili.lib.moss.api.MossResponseHandler',
    'com.bilibili.lib.moss.api.MossRequest', 'org.chromium.net.impl.BidirectionalStreamBuilderImpl',
    'com.bilibili.ship.theseus.keel.player.TheseusKeelPlayer',
    'com.bilibili.ship.theseus.united.player.TripleSpeedService',
    'com.bilibili.playerbizcommonv2.widget.setting.PlayerSettingFunctionWidget2',
    'com.bilibili.playerbizcommonv2.widget.setting.dialog.c',
    'com.bilibili.video.story.setting.StorySpeedDialogManager',
    'tv.danmaku.biliplayerv2.service.interact.biz.IInteractLayerService',
    'tv.danmaku.biliplayerv2.service.interact.biz.chronos.ChronosService',
    'com.bilibili.playerbizcommonv2.widget.speed.TripleSpeedFunctionWidgetV2',
    'com.bilibili.lib.accounts.BiliAccounts',
    'com.bilibili.lib.accounts.model.AccountInfo',
    'okhttp3.RealCall', 'okhttp3.internal.connection.RealCall', 'okhttp3.Response', 'okhttp3.Request', 'okhttp3.ResponseBody',
    'tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.receive.GetDanmakuConfig$SubtitleConfig',
    'tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.send.DanmakuConfigChange$SubtitleConfig']
for package, names in {
    'bilibili.app.playurl.v1': ['PlayViewReq','PlayViewReply','PlayConfReq','PlayConfReply','PlayConfEditReq','ConfType','PlayAbilityConf','PlayConf','PlayConfState','CloudConf','ConfValue','VideoInfo','Stream','StreamInfo'],
    'bilibili.metadata.network': ['Network','NetworkType'],
    'bilibili.app.playerunite.v1': ['PlayViewUniteReq','PlayViewUniteReply'],
    'bilibili.playershared': ['VideoVod','PlayDeviceConf','DeviceConf','ConfValue','VideoCtrl','AutoQnCtl','VodInfo','Stream','StreamInfo'],
    'bilibili.app.view.v1': ['ContinuousPlayReq','ContinuousPlayReply','ViewProgressReply'],
    'bilibili.app.viewunite.v1': ['ViewProgressReply', 'VideoGuide', 'VideoViewPoint'],
    'bilibili.pgc.gateway.player.v2': ['PlayViewReq','PlayViewReply'],
    'bilibili.community.service.dm.v1': ['DmViewReq','DmViewReply','VideoSubtitle','SubtitleItem','SubtitleType','SubtitleAiStatus','SubtitleAiType'],
}.items():
    named += ['com.bapis.' + package + '.' + name for name in names]
for source_file in (repo / 'xposed/src/main/java/app/revanced/bilibili/xposed').glob('Player*.java'):
    named += re.findall(r'"((?:com|tv|org\.chromium|okhttp3|kotlin|kotlinx|[a-z]+\d+)\.[\w.$]+[^.])"', source_file.read_text(encoding='utf-8'))
named_file = build / 'named.txt'
named_file.write_text('\n'.join('L'+n.replace('.', '/')+';' for n in named), encoding='utf-8')
jar = repo.parent / 'reference/revanced-cli-4.6.0.jar'
gson = next(Path('D:/Android/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0').rglob('*.jar'))
classpath = str(jar) + ';' + str(gson)
def run(command):
    result = subprocess.run(command, capture_output=True, encoding='utf-8')
    print(result.stdout, end='')
    if result.returncode:
        print(result.stderr, file=sys.stderr)
        raise SystemExit(result.returncode)
run(['javac', '-J-Dfile.encoding=UTF-8', '-J-Dsun.stdout.encoding=UTF-8', '-J-Dsun.stderr.encoding=UTF-8',
     '-encoding', 'UTF-8', '-cp', classpath, '-d', str(build), str(Path(__file__).with_name('PlayerDexInventory.java'))])
run(['java', '-Xmx3g', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8', '-Dsun.stderr.encoding=UTF-8',
     '-cp', str(build) + ';' + classpath, 'PlayerDexInventory',
     str(repo.parent / '哔哩哔哩8.27.0原版.apk'), str(queries), str(build / 'classes.json'), str(named_file)])
data = json.loads((build / 'classes.json').read_text(encoding='utf-8'))
summary = {k: v['matches'] for k, v in data.items()}
(build / 'matches.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print('Saved matched classes and method bodies to player-inventory; queries:', len(needles))
