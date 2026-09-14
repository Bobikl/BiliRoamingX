# 仅宿主设置与 v2 目录故障处理

版本 `1.23.3-lsposed.3` / code 3；目标仍为官方粉版 8.27.0 / 8270400、Modern API 102。

按用户最新要求，设置仅能在“哔哩哔哩 → 设置 → 哔哩漫游X”打开。模块 APK 不再包含独立设置 Activity 或启动入口，也不包含 Service、Receiver、Provider、自定义 Application 或 libxposed service 实现依赖。

## 已确认的故障

用户反馈 v2 内嵌页无法显示底栏，而 LSPosed 打开的独立设置页可以显示。经授权只读检查手机日志与软件包注册信息，确认：

- 设置入口成功插入，宿主全屏设置页成功显示。
- 当前模块日志中有 36 条 `Unknown authority app.revanced.bilibili.xposed.catalog` 失败：28 条 `Settings.bridge/settings_snapshot`，8 条 `BottomBar.catalog/report`；没有把 verbose 中的重复事件再计入。
- 系统包信息显示 CatalogProvider 已注册、模块 code 对应 v2、用户 0 下已安装且启用；因此不是 APK 漏掉 Provider。
- 哔哩哔哩自身的查询声明不包含本模块。Android 包可见性限制与这种现象相符；尚未进一步区分 Android 原生过滤、ROM 或其他隐藏模块的影响。Android 的[包可见性说明](https://developer.android.com/training/package-visibility)及[Provider URI 可见性说明](https://developer.android.com/training/package-visibility/use-cases#grant-uri-access)可作机制参考。

相关模块日志已只读导出到 [device-logs-v2-failure.json](device-logs-v2-failure.json)。独立页可以读取其本地缓存，不代表宿主当前仍能回传新目录。

用户随后要求取消 LSPosed 内的独立打开功能，因此最终没有加入授权握手或扩大包可见性；改为移除整个跨进程依赖。

## 最终实现

`HostRuntime` 建立唯一的 `LocalSettingsStore`。Hook 读取该对象的宿主 SharedPreferences；设置页直接修改同一对象；底栏目录通过普通 Java 方法交给它保存并通知当前页面。保存和目录写入由同一个后台队列处理，不在 UI 线程等待 Provider。

宿主配置文件名为 `biliroamingx_lsposed_settings`，目录记录为 `biliroamingx_lsposed_catalog`。内嵌 UI 保留全屏 Dialog、原生 Preference 入口、去重、系统栏/挖孔避让和销毁清理。

204 项设置定义直接从框架提供的模块 APK 路径读取 assets，不查询模块包、不使用宿主资源 ID。如果完整目录读取失败，日志与页面明确提示，并保留底栏和调试项的基础类型定义。

只保留 Modern API 中的旧配置读取用于一次迁移：成功后写入迁移标记，正常启动不再依赖旧框架配置。仅迁移已知且类型有效的键；不覆盖已存在的本地值，恢复默认也有编辑记录，避免失败重试时把旧值重新写回来。未知键、内部键和无效值不导入。迁移失败不阻止本地设置使用，下次宿主启动重试。

当前功能范围仍是底栏过滤；其他未移植项只保存配置，未增加原功能回调。清除宿主数据会清除本地配置；只卸载模块不一定清除宿主内的配置文件。

## 验证结果

- `assembleRelease` 成功。
- 底栏规则 4/4、设置编辑校验 6/6、旧配置迁移 4/4，共 14 项纯 JVM 测试通过。
- `lintRelease`：0 errors、25 warnings；主要为原有私有 attach 反射、版本及本地化建议。Android 测试工作进程的历史问题没有在本轮修复。
- 最终 APK 反解 Manifest 确认没有 Activity、activity-alias、Service、Receiver、Provider、自定义 Application，也不请求权限。
- APK 不含旧 Provider authority、libxposed service 实现、嵌套 APK/JAR、宿主 dummy 或黑名单标记；API 102 元数据与 204 项定义校验通过。见 [module-artifact-check-v3.json](module-artifact-check-v3.json)。
- APK 签名与前版相同，可覆盖安装。网络盘复制校验见 [delivery-check-v3.json](delivery-check-v3.json)。

本轮手机操作仅为授权的只读诊断。没有安装新版、操作界面、清数据或重启。v3 的本地目录显示、首次迁移和保存效果尚待用户真机验证。

## 验收

覆盖安装 `BiliRoamingX-LSPosed-1.23.3-bili8.27.0-host-only-v3-test.apk`，保留 LSPosed 启用和作用域；彻底关闭再打开哔哩哔哩，先进入首页，再进入设置里的“哔哩漫游X”。检查底栏列表、旧选择、保存、重启后生效及恢复显示全部。不要先卸载旧模块或清宿主数据，以保留迁移来源。
