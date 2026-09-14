"""Check every new JSON reflection field against the official 8.27.0 DEX."""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import subprocess
import sys

sys.stdout.reconfigure(encoding='utf-8')
repo = Path(__file__).resolve().parents[2]
apk = repo.parent / '哔哩哔哩8.27.0原版.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == '00ad96b15626026c9880f08c2ff3c5a8059c08eb1683928bb0e6ed2d0b37cd7c'
main = 'tv.danmaku.bili.ui.main2.resource.MainResourceManager$'
room = 'com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo.'
user = 'com.bilibili.bililive.videoliveplayer.net.beans.gateway.userinfo.'
shop = 'com.bilibili.bililive.room.biz.shopping.beans.'
targets = {
    main + 'TabResponse': ['tabData'], main + 'TabData': ['bottom', 'top', 'tab', 'topLeftInfo'],
    main + 'Tab': ['tabId:Ljava/lang/String;', 'name:Ljava/lang/String;', 'uri:Ljava/lang/String;'],
    main + 'TopLeftInfo': ['url:Ljava/lang/String;'],
    'com.bilibili.okretro.GeneralResponse': ['data'],
    'tv.danmaku.bili.ui.main2.api.AccountMine': ['sectionListV2:Ljava/util/List;', 'liveTip', 'gameTips'],
    'com.bilibili.lib.homepage.mine.MenuGroup': ['title:Ljava/lang/String;', 'itemList:Ljava/util/List;', 'button'],
    'com.bilibili.lib.homepage.mine.MenuGroup$Item': ['id:J', 'title:Ljava/lang/String;', 'uri:Ljava/lang/String;', 'redDot:I', 'redDotRorNew:Z'],
    'com.bilibili.lib.homepage.mine.MenuGroup$MineButton': ['text:Ljava/lang/String;'],
    'com.bilibili.app.authorspace.api.BiliSpace': ['tab:Ljava/util/List;', 'buttonEntranceList:Ljava/util/List;',
        'liveEntry', 'chargeResult', 'guard', 'ad', 'adV2', 'archiveVideo', 'article', 'audio', 'season', 'coinVideo',
        'recommendVideo', 'followComicList', 'spaceGame', 'cheeseVideo', 'fansDress', 'favoriteBox', 'comicList',
        'ugcSeasonList', 'contractResource', 'nftShowModule'],
    'com.bilibili.app.authorspace.api.BiliSpace$Tab': ['param:Ljava/lang/String;'],
    'com.bilibili.app.authorspace.api.BiliSpaceButtonEntrance': ['moduleType:Ljava/lang/String;'],
    'tv.danmaku.bili.ui.splash.ad.model.SplashData': ['splashList:Ljava/util/List;', 'strategyList:Ljava/util/List;'],
    'tv.danmaku.bili.ui.splash.ad.model.SplashShowData': ['strategyList:Ljava/util/List;'],
    'tv.danmaku.bili.ui.splash.brand.model.BrandSplashData': ['brandList:Ljava/util/List;', 'preloadList:Ljava/util/List;', 'queryList:Ljava/util/List;', 'showList:Ljava/util/List;'],
    'tv.danmaku.bili.ui.splash.event.EventSplashDataList': ['eventList:Ljava/util/List;'],
    'tv.danmaku.bili.ui.splash.event.EventSplashData': [],
    'tv.danmaku.bili.ui.main.event.model.EventEntranceModel': [],
    'com.bilibili.app.comm.list.widget.recommend.RecommendModeGuidanceConfig': [],
    'com.bilibili.ad.adview.videodetail.danmakuv2.model.DmAdvert': ['ads:Ljava/util/List;'],
    shop + 'LiveShoppingInfo': ['shoppingCardDetail', 'recommendCardDetail'],
    shop + 'LiveGoodsCardInfo': [], shop + 'LiveShoppingRecommendCardGoodsDetail': [], shop + 'LiveShoppingGotoBuyInfo': [],
    'com.bilibili.bililive.videoliveplayer.net.beans.attentioncard.LiveRoomRecommendCard': [],
    'com.bilibili.bililive.room.biz.reverse.bean.LiveRoomReserveInfo': ['showReserveDetail:Z'],
    room + 'BiliLiveRoomInfo$DmComboInfo': [], room + 'LiveRoomDanmakuVoteCardInfo': [],
    room + 'BiliLiveRoomInfo': ['functionCard', 'bannerInfo', 'dmComboInfo', 'danmakuVoteCard', 'areaMaskInfo', 'blockInfo', 'newSwitchInfo:Ljava/util/Map;'],
    room + 'BiliLiveRoomInfo$FunctionCard': ['followCard', 'wishlistCard'],
    user + 'BiliLiveRoomUserInfo': ['functionCard', 'taskInfo', 'playTogetherInfo', 'playTogetherInfoV2', 'qoe'],
    user + 'FunctionCard': ['sengGiftCard'],
}
def verify(item):
    cls, fields = item
    command = ['java', '-Dfile.encoding=UTF-8', '-Dsun.stdout.encoding=UTF-8',
        '-Dcom.android.sdklib.toolsdir=D:/Android/Sdk/cmdline-tools/latest', '-classpath',
        'D:/Android/Sdk/cmdline-tools/latest/lib/apkanalyzer-classpath.jar',
        'com.android.tools.apk.analyzer.ApkAnalyzerCli', 'dex', 'code', '--class', cls, str(apk)]
    code = subprocess.run(command, check=True, capture_output=True, encoding='utf-8').stdout
    assert '.class ' in code, cls
    declarations = [line for line in code.splitlines() if line.startswith('.field ')]
    matched = []
    for field in fields:
        marker = ' ' + field + (':' if ':' not in field else '')
        hits = [line for line in declarations if marker in line]
        assert len(hits) == 1, (cls, field, hits)
        matched.append(hits[0])
    if cls.endswith('.EventSplashData'):
        assert '.method public final isBirthdayData()Z' in code
        matched.append('.method public final isBirthdayData()Z')
    return cls, matched
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    results = dict(pool.map(verify, targets.items()))
report = {'host_sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'checks': 'passed',
          'class_count': len(results), 'member_count': sum(map(len, results.values())), 'declarations': results,
          'excluded_live_options': ['shoppingSelected', 'giftStar'], 'device_execution': False}
text = json.dumps(report, ensure_ascii=False, indent=2) + '\n'
target = repo / 'docs/json-host-check-v4.json'
target.write_text(text, encoding='utf-8')
assert target.read_text(encoding='utf-8') == text
print('Verified', report['member_count'], 'members in', len(results), 'host classes')
