package com.bilibili.bililive.videoliveplayer.net.beans.gateway.roominfo;

import java.util.Map;

/** Test-only model; live and unrelated data should not be mixed. */
public class BiliLiveRoomInfo {
    public FunctionCard functionCard = new FunctionCard();
    public Object bannerInfo = new Object(), dmComboInfo = new Object(), danmakuVoteCard = new Object();
    public Object areaMaskInfo = new Object(), blockInfo = new Object();
    public Map<String, Object> newSwitchInfo = Map.of("room-player-watermark", 1, "other", 2);
    public static class FunctionCard { public Object followCard = new Object(), wishlistCard = new Object(); }
}
