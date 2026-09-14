package app.revanced.bilibili.xposed;

final class ModuleConstants {
    static final String HOST = "tv.danmaku.bili";
    static final String HOST_VERSION = "8.27.0";
    static final long HOST_CODE = 8270400L;
    static final String HOST_CERT_SHA256 = "93ba270f5521139ecafe4bb638ac5b1198bc548f62d9fd8f8580a079faf5910e";
    static final String GROUP = "settings";
    static final String BOTTOM_KEY = "showing_bottom_items";
    static final String REVISION = "_lsposed_config_revision";
    static final String LOCAL_SETTINGS = "biliroamingx_lsposed_settings";
    static final String LOCAL_CATALOG = "biliroamingx_lsposed_catalog";
    static final String MIGRATED = "_lsposed_remote_migrated_v1";
    static final String EDITED_KEYS = "_lsposed_local_edited_keys";
    static final String TAG = "BiliRoamingX-LSPosed";

    private ModuleConstants() {}
}
