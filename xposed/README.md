# 哔哩漫游X：LSPosed 移植

当前测试版 `1.23.3-lsposed.5`，仅从“哔哩哔哩 → 设置 → 哔哩漫游X”打开设置。没有独立 Activity、桌面入口、Service、Receiver 或 Provider。用户已确认 v3 底栏与宿主内设置生效；v5 播放器功能尚待用户真机验证。

设置目录沿用原版 XML 的分类、顺序、标题、说明和选项，重绘为哔哩哔哩风格的列表、粉色开关和勾选控件，支持深色模式与多级返回。50 个页面覆盖全部 204 项 Settings；未移植项在控件位置显示“未移植”，不可编辑。11 项不在当前页面中的内部配置集中列于“其他配置”。没有恢复原版所有动态 widget 的布局或宿主 Garb 皮肤资源，不宣称像素级一致。

本版有 45 项配置接入运行逻辑。本轮新增播放器画质、倍速、手势、播放配置、字幕样式及导入导出，具体范围见 [v5 报告](../docs/player-port-v5-report.md)。自动翻译按用户要求暂缓，泰区 AccessKey 未移植。此前净化范围见 [v4 报告](../docs/host-only-v4-report.md)。这不是全部 91 个 Patch 的完成版。

## 安装与验证

1. 覆盖安装 `BiliRoamingX-LSPosed-1.23.3-bili8.27.0-player-v5-test.apk`。
2. 保持 LSPosed 启用，作用域勾选官方粉版哔哩哔哩 8.27.0（8270400）。要求支持 Modern API 102 的 LSPosed。
3. 彻底关闭再打开哔哩哔哩，进入首页和“我的”，再从 App 设置打开“哔哩漫游X”。
4. 底栏、“我的”项目和其他选项点击后自动保存，重启 App 后检查效果。
5. 分别测试空间、开屏、直播净化；未移植选项应只显示文字且无法修改。检查多级返回、深色模式、设置保留、登录及切换账号。

配置保存在宿主 SharedPreferences `biliroamingx_lsposed_settings`，首次尝试迁移旧模块的已知有效设置；失败后下次启动重试，并保留已在宿主修改的值。清除哔哩哔哩数据会删除配置和导入字体。试看选项读取宿主当前账号的 VIP 状态；主站 AccessKey 由用户手动填写，不记入日志。

## 构建与检查

工程使用 Java 17、Gradle 9.4.1、AGP 9.2.1、SDK 37、minSdk 26。在本目录执行：

```powershell
python tools/generate_settings_schema.py
python tools/generate_settings_pages.py
.\gradlew.bat assembleRelease verifyBottomBarPolicy verifySettingsRules verifyJsonFeatures verifyPlayerRules lintRelease
python tools/verify_json_host.py
python tools/inventory_player.py
python tools/verify_player_host.py
python tools/check_artifact.py build/outputs/apk/release/BiliRoamingX-LSPosed-release.apk --host-only --sdk D:/Android/Sdk --report ../docs/module-artifact-check-v5.json
```

四组 JVM 检查共 38 项测试。新增播放器 DEX 校验覆盖 93 类、230 项声明；原 JSON 校验覆盖 32 类、74 个字段或方法。测试替身仅位于 `src/test`，不打入模块。宿主验证工具检查本地官方 APK 的固定 SHA-256，未连接手机。

Release 使用与前版一致的本机 Android debug 证书签名，可覆盖安装，属于本地测试版。API 保持 `compileOnly`，模块不携带原 integrations APK、宿主类或远程黑名单逻辑。

基于 BiliRoamingX 1.23.3 / GPL-3.0。历史报告见 [v3](../docs/host-only-settings-report.md) 与 [首轮](../docs/first-round-report.md)。
