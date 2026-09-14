"""Verify player reflection contracts against the locally inventoried official APK."""
from pathlib import Path
import hashlib
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')
repo = Path(__file__).resolve().parents[2]
apk = repo.parent / '哔哩哔哩8.27.0原版.apk'
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
assert sha == '00ad96b15626026c9880f08c2ff3c5a8059c08eb1683928bb0e6ed2d0b37cd7c'
inventory = repo / 'xposed/build/player-inventory/classes.json'
data = json.loads(inventory.read_text(encoding='utf-8'))
checked, failures = [], []

def descriptor(name):
    return name if name.startswith('L') and name.endswith(';') else 'L' + name.replace('.', '/') + ';'

def method(owner, name, params=None, returns=None):
    owner = descriptor(owner)
    cls = data.get(owner, {})
    found = [m for m in cls.get('methods', []) if m['name'] == name
             and (params is None or m['params'] == params) and (returns is None or m['returns'] == returns)]
    label = owner + '->' + name + str(params or [])
    if not found:
        failures.append(label)
        return None
    checked.append(label)
    return found[0]['returns']

def field(owner, name, kind=None):
    owner = descriptor(owner)
    found = [f for f in data.get(owner, {}).get('fields', []) if f.startswith(name + ':' + (kind or ''))]
    label = owner + '->' + name
    (checked if found else failures).append(label)

def child(owner, name):
    result = method(owner, 'get' + name, [])
    method(owner, 'has' + name, [], 'Z')
    if result:
        method(owner, 'set' + name, [result], 'V')
    return result

ugc = 'com.bapis.bilibili.app.playurl.v1.'
unite = 'com.bapis.bilibili.app.playerunite.v1.'
shared = 'com.bapis.bilibili.playershared.'
dm = 'com.bapis.bilibili.community.service.dm.v1.'
for owner in [ugc + 'PlayViewReq', shared + 'VideoVod', 'com.bapis.bilibili.pgc.gateway.player.v2.PlayViewReq']:
    method(owner, 'setFnval', ['I'], 'V'); method(owner, 'setFourk', ['Z'], 'V')
for owner in [ugc + 'PlayViewReq', shared + 'VideoVod']:
    method(owner, 'getDownload', [], 'I')
child(unite + 'PlayViewUniteReq', 'Vod')
field(ugc + 'ConfType', 'LOSSLESS_VALUE', 'I')
method(ugc + 'PlayConfEditReq', 'getPlayConfList', [], 'Ljava/util/List;')
method(ugc + 'PlayConfState', 'getConfTypeValue', [], 'I')
method(ugc + 'PlayConfState', 'getConfValue', [])
method(ugc + 'ConfValue', 'getSwitchVal', [], 'Z')
for owner in [ugc + 'PlayConfReply', ugc + 'PlayViewReply']:
    conf = child(owner, 'PlayConf')
    if conf:
        lossless = child(conf, 'LossLessConf')
        if lossless:
            value = child(lossless, 'ConfValue')
            if value: method(value, 'setSwitchVal', ['Z'], 'V')
child(unite + 'PlayViewUniteReply', 'PlayDeviceConf')
method(shared + 'PlayDeviceConf', 'getMutableDeviceConfsMap', [], 'Ljava/util/Map;')
value = child(shared + 'DeviceConf', 'ConfValue')
if value: method(value, 'setSwitchVal', ['Z'], 'V')
method(ugc + 'PlayViewReply', 'clearAb', [], 'V')
method(unite + 'PlayViewUniteReply', 'clearQnTrialInfo', [], 'V')
for owner, suffix, stream in [(ugc + 'PlayViewReply', 'VideoInfo', ugc + 'Stream'),
                              (unite + 'PlayViewUniteReply', 'VodInfo', shared + 'Stream')]:
    video = child(owner, suffix)
    if video:
        method(video, 'getStreamListList', [], 'Ljava/util/List;')
        method(video, 'setStreamList', ['I', descriptor(stream)], 'V')
    method(stream, 'hasDashVideo', [], 'Z')
    info = child(stream, 'StreamInfo')
    if info:
        method(info, 'getNeedVip', [], 'Z')
        method(info, 'setNeedVip', ['Z'], 'V'); method(info, 'setVipFree', ['Z'], 'V')
child(unite + 'PlayViewUniteReply', 'VideoCtrl'); child(shared + 'VideoCtrl', 'AutoQnCtl')
for name in ['LoginFull', 'NologinFull', 'MobileLoginFull', 'MobileNologinFull', 'LoginHalf', 'NologinHalf']:
    method(shared + 'AutoQnCtl', 'set' + name, ['J'], 'V')
    method(shared + 'AutoQnCtl', 'get' + name, [], 'J')
method('com.bapis.bilibili.app.view.v1.ContinuousPlayReply', 'clearRelates', [], 'V')
method('com.bapis.bilibili.app.view.v1.ViewProgressReply', 'setPointPermanent', ['Z'], 'V')
guide = child('com.bapis.bilibili.app.viewunite.v1.ViewProgressReply', 'VideoGuide')
if guide:
    point = child(guide, 'VideoPoint')
    if point: method(point, 'setPointPermanent', ['Z'], 'V')
method(dm + 'DmViewReq', 'getOid', [], 'J')
child(dm + 'DmViewReply', 'Subtitle')
method(dm + 'VideoSubtitle', 'getSubtitlesList', [], 'Ljava/util/List;')
method(dm + 'VideoSubtitle', 'addSubtitles', [descriptor(dm + 'SubtitleItem')], 'V')
method(dm + 'VideoSubtitle', 'setSubtitles', ['I', descriptor(dm + 'SubtitleItem')], 'V')
for name in ['IdStr', 'Lan', 'LanDoc', 'LanDocBrief', 'SubtitleUrl']:
    method(dm + 'SubtitleItem', 'get' + name, [], 'Ljava/lang/String;')
    method(dm + 'SubtitleItem', 'set' + name, ['Ljava/lang/String;'], 'V')
method(dm + 'SubtitleItem', 'setId', ['J'], 'V')
method(dm + 'SubtitleItem', 'setType', [descriptor(dm + 'SubtitleType')], 'V')
for enum, values in [('SubtitleType', ['AI', 'CC']), ('SubtitleAiStatus', ['Assist']), ('SubtitleAiType', ['Translate'])]:
    for value in values: field(dm + enum, value)

contracts = {
    'com.bilibili.lib.blconfig.internal.ABSource': ['f'],
    'com.bilibili.lib.blconfig.internal.ConfigSource': ['g'],
    'com.bilibili.lib.dd.internal.DDContractImpl': ['getBoolean', 'a'],
    'com.bilibili.playerbizcommon.utils.PlayerSettingHelper': ['getDefaultQuality'],
    'com.bilibili.lib.blrouter.RouteRequest': ['<init>'],
    'com.bilibili.lib.blrouter.RouteRequest$Builder': ['<init>'],
    'com.bilibili.lib.moss.api.MossServiceImp': ['blockingUnaryCall', 'asyncUnaryCall', 'asyncServerStreamingCall'],
    'com.bilibili.lib.moss.api.MossResponseHandler': ['onNext'],
    'com.bilibili.player.tangram.basic.PlaySpeedManagerImpl': ['<init>'],
    'b42.d': ['C'], 'uy1.a': ['C'], 'com.bilibili.playerbizcommonv2.widget.speed.e': ['C'],
    'aj3.p0': ['z1', 'setPlaySpeed'],
    'com.bilibili.playerbizcommon.widget.function.setting.b0': ['<init>', 'N1', 'K1', 'J1'],
    'com.bilibili.playerbizcommonv2.widget.setting.PlayerSettingFunctionWidget2': ['z0'],
    'com.bilibili.ship.theseus.united.page.toolbar.MenuService': ['t0'],
    'com.bilibili.ship.theseus.united.page.toolbar.MenuService$createSpeed$1': ['invoke'],
    'com.bilibili.playerbizcommonv2.widget.setting.PlayerSettingFunctionWidget2$createSpeed$1$1': ['invoke'],
    'com.bilibili.video.story.setting.StorySpeedDialogManager$createDialog$onSelect$1': ['invoke'],
    'com.bilibili.video.story.setting.StorySpeedDialogManager': ['b', 'c'],
    'com.bilibili.playerbizcommonv2.widget.setting.dialog.c$a': ['<init>'],
    'com.bilibili.playerbizcommonv2.widget.setting.dialog.c$b': ['<init>', 'a', 'b', 'c'],
    'com.bilibili.ship.theseus.united.player.TripleSpeedService$mListener$1$onLongPress$1': ['<init>'],
    'com.bilibili.ship.theseus.united.player.TripleSpeedService$showNewTripleSpeedWidget$1$1': ['invokeSuspend'],
    'com.bilibili.ship.theseus.keel.player.TheseusKeelPlayer': ['q'],
    'com.mall.videodetail.vd.united.player.TripleSpeedService': ['w', 'x'],
    'com.bilibili.playerbizcommon.gesture.g$b': ['onLongPress'],
    'com.bilibili.playerbizcommon.gesture.GestureService$m': ['onScaleBegin', 'onScale', 'onScaleEnd', 'onScroll', 'onRotate'],
    'com.bilibili.playerbizcommon.gesture.GestureService': ['access$getMPlayerContainer$p'],
    'com.bilibili.playerbizcommon.gesture.y': ['onWidgetShow', 'onControlContainerVisibleChanged'],
    'tv.danmaku.biliplayerv2.service.IRenderContainerService': ['setAspectRatio', 'resetRenderContainer'],
    'tv.danmaku.ijk.media.player.IjkMediaPlayerItem': ['setItemOptions'],
    'tv.danmaku.ijk.media.player.IjkMediaAsset$MediaAssertSegment$Builder': ['build'],
    'org.chromium.net.impl.BidirectionalStreamBuilderImpl': ['build'],
    'com.bilibili.lib.accounts.BiliAccounts': ['get', 'mid'],
    'com.bilibili.cron.Canvas': ['measureTextFromLayout', 'measureTextImpl', 'drawPath', 'drawText'],
    'com.bilibili.playerbizcommon.widget.function.setting.r': ['createContentView'],
    'nz1.f': ['createContentView'], 'okhttp3.g': ['f', 'isCanceled'],
    'okhttp3.Response$Builder': ['<init>', 'request', 'protocol', 'code', 'message', 'addHeader', 'body', 'build'],
    'okhttp3.Request': ['url'], 'okhttp3.ResponseBody': ['create'],
}
for owner, names in contracts.items():
    for name in names: method(owner, name)
for owner in ['com.bilibili.playerbizcommonv2.widget.quality.m', 'com.mall.videodetail.vd.united.page.videoquality.w']:
    method(owner, 'K1')
for owner, names in {
    'com.bilibili.cron.Canvas': ['paint', 'maxWidth', 'staticLayout', 'alignment', 'fillColor', 'strokeColor'],
    'org.chromium.net.impl.BidirectionalStreamBuilderImpl': ['mUrl', 'mRequestHeaders'],
    'tv.danmaku.ijk.media.player.IjkMediaPlayerItem': ['mIjkMediaConfigParams'],
    'tv.danmaku.ijk.media.player.IjkMediaConfigParams': ['mEnableHwCodec'],
    'tv.danmaku.ijk.media.player.IjkMediaAsset$MediaAssertSegment': ['url', 'backupUrls'],
    'com.bilibili.player.tangram.basic.PlaySpeedManagerImpl': ['a'],
    'com.bilibili.playerbizcommon.widget.function.setting.b0': ['d', 'k', 'm', 'n'],
    'com.bilibili.playerbizcommonv2.widget.speed.f': ['d'], 'uy1.b': ['d'], 'wo2.a': ['c'],
    'com.bilibili.music.podcast.segment.AbsMusicPlayerPanelSegment': ['n'],
    'com.bilibili.music.podcast.view.PodcastSpeedSeekBar': ['v'],
    'com.bilibili.ship.theseus.ogv.intro.kingposition.OgvKingPositionShareService': ['v'],
    'com.bilibili.bangumi.logic.page.detail.service.refactor.NewShareService': ['v'],
    'com.mall.videodetail.vd.united.page.toolbar.MenuService': ['I'],
    'com.bilibili.playerbizcommon.gesture.GestureService$m': ['c'],
    'com.bilibili.playerbizcommon.gesture.y': ['e'],
    'com.bilibili.playerbizcommon.widget.function.setting.r': ['d'], 'nz1.f': ['f'], 'okhttp3.g': ['f'],
}.items():
    for name in names: field(owner, name)
for owner in ['receive.GetDanmakuConfig', 'send.DanmakuConfigChange']:
    method('tv.danmaku.biliplayerv2.service.interact.biz.chronos.chronosrpc.methods.' + owner + '$SubtitleConfig',
           'setBottomMargin', ['Ljava/lang/Float;'], 'V')
service = 'tv.danmaku.biliplayerv2.service.interact.biz.IInteractLayerService'
for name in ['getDanmakuParams', 'setDmViewReply', 'loadSubtitle']:
    method(service, name)
method(service, 'recordSelectedSubtitle', ['Z', 'Z'], 'V')
report = {'host_sha256': sha, 'inventory_sha256': hashlib.sha256(inventory.read_bytes()).hexdigest(),
          'checks': len(checked), 'class_count': len({s.split('->')[0] for s in checked}),
          'failures': failures, 'scope': 'DEX declarations only; user validates runtime on phone'}
content = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
target = repo / 'docs/player-host-check-v5.json'
target.write_text(content, encoding='utf-8')
assert target.read_text(encoding='utf-8') == content
print(content)
assert not failures, 'Missing player contracts: inspect inventory coverage and implementation'
