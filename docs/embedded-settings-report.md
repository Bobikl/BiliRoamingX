# 宿主内嵌设置与日志分析（2026-09-09）

后续：用户真机确认 v2 内嵌入口能显示，但 Provider 目录读取失败。按最新要求，v3 已改为仅宿主设置，详见 [v3 报告](host-only-settings-report.md)。以下为 v2 当时的实现与验证记录。

版本：`1.23.3-lsposed.2`，versionCode 2。官方粉版 8.27.0 / 8270400，Modern API 102。

用户已确认上一版底栏隐藏在手机上生效。本次新增“哔哩哔哩 → 设置 → 哔哩漫游X”入口和宿主内的完整模块设置页。新版已完成构建与静态验证，尚未安装到手机验证入口或保存流程。

## 日志结论

经用户明确授权，通过 ADB 和已有 su 权限，只读检查了 `/data/adb/lspd/log` 中的当前模块日志、详细日志，以及相关 logcat 和 crash 缓冲区。没有安装 APK、修改手机配置、清日志、重启或操作界面。

| 日志记录时间（原日志时间） | 结果 |
| --- | --- |
| 2026-09-09 22:49:56.028 / 22:49:56.247 | 模块入口加载；3/3 个 parseObject Hook 安装成功 |
| 2026-09-09 22:50:35.180 / 22:50:36.833 | 模块入口加载；3/3 个 parseObject Hook 安装成功 |
| 2026-09-09 22:50:53.651 / 22:50:55.216 | 模块入口加载；3/3 个 parseObject Hook 安装成功 |

来源：`modules_2026-09-06T12:15:00.743859.log` 和 `verbose_2026-09-06T12:15:00.743612.log`。两份日志中的相同事件不重复计数。

读取范围内未发现本模块异常。按模块标签读取的 logcat 没有返回条目，crash 缓冲区筛选也未返回宿主/模块相关条目；不据此断言所有时间、所有路径均无错误。安装日志只证明 Hook 安装，底栏实际生效的证据来自用户反馈。

22:50:55.643 存在 NoActive 临时解冻模块进程的记录。此记录本身不是模块错误，也没有观察到因冻结导致的调用失败。新内嵌页将 Provider 读取和保存放到后台线程，避免宿主 UI 线程等待模块进程。

后续 ADB 连接断开，自动化日志导出未能完成；上表依据断开前成功读取的输出整理。未生成完整日志归档。`xposed/tools/read_module_logs.py` 可在用户再次授权且设备连接时，只读导出本模块日志。

## 内嵌方案

参考用户提供的《宿主内嵌设置实现与原理.md》。采用同样的宿主 Activity + 全屏系统 Dialog 方式，窗口属于哔哩哔哩。没有为打开内嵌页启动模块 Activity，不使用悬浮窗权限，也不代理 Activity 启动。

入口适配真实的 `BiliPreferencesActivity$BiliPreferencesFragment.onCreatePreferences(Bundle,String)`：先执行宿主原方法一次，再向 PreferenceScreen 插入带唯一 key 的原生 Preference。反射成员在安装时缓存；监听器类型从 setter 推导，适配原版中混淆为 `Preference$d` 的接口。重复进入通过 key 检查避免重复添加。失败记录目标及异常，保留宿主设置页。

`SettingsScreen` 是共用 UI，独立 SettingsActivity 和宿主 Dialog 使用同一份控件与编辑逻辑。204 项配置仍可编辑；未移植功能仍明确标为“仅保存”。底栏规则没有改变。

Dialog 使用宿主 Activity 的主题包装 Context 和系统控件，不向宿主注入模块资源。新增 WindowInsets/挖孔避让、明暗系统栏、返回关闭、宿主销毁时关闭、编辑弹窗清理和异步结果的关闭状态检查。没有常驻轮询。旋转后不会自动恢复未保存编辑，仍需真机检查 ROM 的返回和窗口行为。

## 与参考文档的配置差异

参考项目把配置放在宿主 SharedPreferences。本项目延续第一轮用户要求的 Remote Preferences，保留已有配置以及独立入口与内嵌页的一致性。

宿主内嵌页通过已有 Provider 的 `settings_snapshot/settings_save` 调用读取和请求保存；模块 UID 才执行 Remote Preferences 写入。该通信可能启动/唤醒模块进程，不能宣称本版是完全无跨进程的纯宿主存储方案。页面本身始终在宿主窗口内。

Provider 校验调用 UID 属于粉版，并对设置调用核对官方签名。写入限制为每次一个已知配置键，校验六种类型、集合内容、数值和大小；内部配置版本键不可由宿主指定，空底栏集合拒绝保存。独立页与内嵌页共用串行写入队列，只有框架确认 commit 后才返回保存成功。不记录设置值。

## 验证

- `assembleRelease` 成功。
- `lintRelease`：0 errors、27 warnings，包括原有的非公开 attach 反射、导出 Provider、目标版本及本地化建议；没有宣称零警告。
- `verifyBottomBarPolicy`：4/4 通过。
- `verifySettingsBridge`：6/6 通过，覆盖六种类型、恢复默认、未知/内部键拒绝、错误类型、非有限浮点数、空底栏与请求大小/数量。
- 原版 APK 静态检查：6 个类、15 个公开方法匹配，见 [settings-host-check.json](settings-host-check.json)。
- APK 入口/API 102/作用域/204 项设置/禁止打包内容/黑名单标记扫描通过，见 [module-artifact-check-v2.json](module-artifact-check-v2.json)。
- v2 APK 签名有效，证书 SHA-256 与上一版相同：`9ac1989e811dca262387317264b7ecfc72535278c2c6af0cf44b40344811db12`。
- 本机 Android `testDebugUnitTest` 工作进程问题未在本轮修复；上述两组为独立 JVM 测试，不代表 Android UI/IPC 测试通过。

## 手机验收

1. 覆盖安装 `BiliRoamingX-LSPosed-1.23.3-bili8.27.0-embedded-v2-test.apk`，保留 API 102 作用域，彻底关闭再打开哔哩哔哩。
2. 进入哔哩哔哩设置，确认“哔哩漫游X”仅出现一次，点击后不跳转独立模块。
3. 在内嵌页改变底栏选择并保存，检查独立页显示同一设置；重启哔哩哔哩后检查效果，再恢复原选择。
4. 检查返回、重复打开、明暗主题、系统栏、横竖屏、后台切回，以及模块进程被冻住/未连接时的错误提示。
5. 如失败，日志新增标识为 `Settings.entrance`、`Settings.open`、`Settings.bridge`。安装成功与入口实际显示仍分开判断。

APK 同步到 `X:\临时同步\BiliRoamingX-LSPosed-first-round`，文档保存在本地工程。网络盘副本哈希见 [delivery-check-v2.json](delivery-check-v2.json)。
