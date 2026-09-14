# 哔哩漫游X：LSPosed 移植

当前版本 `1.23.3-lsposed.3`：只能从“哔哩哔哩 → 设置 → 哔哩漫游X”打开设置。没有独立 Activity、桌面入口、Service、Receiver 或 Provider；设置、底栏目录和 Hook 在宿主主进程内直接共享。

用户已确认底栏隐藏和 v2 内嵌入口能显示；v2 的目录读取因 Provider 无法访问而失败。本版按用户新要求移除此通信路径，实际目录显示、配置迁移和保存仍待用户验证。见 [仅宿主设置报告](../docs/host-only-settings-report.md)。

基于 BiliRoamingX 1.23.3 / GPL-3.0。第一轮只实现 Json Patch 的底部导航过滤，不是全部 91 个 Patch 的完整移植。

要求支持 Modern API 102 的 LSPosed，宿主为官方粉版 8.27.0（8270400）。模块自身 minSdk 26。宿主包名、版本和签名均有检查。

## 手机验证步骤（由用户执行）

1. 覆盖安装交付目录顶层的 `BiliRoamingX-LSPosed-1.23.3-bili8.27.0-host-only-v3-test.apk`，不卸载旧模块、不清除哔哩哔哩数据。
2. 在 LSPosed 启用模块，作用域勾选粉版哔哩哔哩。
3. 彻底关闭再打开哔哩哔哩，先进入首页，再从哔哩哔哩设置进入“哔哩漫游X”。
4. 确认实际底栏列表出现，并检查旧版选择是否已迁移。LSPosed 仍用于启用和设置作用域，不再提供独立设置页。
5. 取消一个底栏按钮的勾选并保存，再彻底关闭并重新打开哔哩哔哩，检查按钮是否隐藏。
6. 返回模块设置，核对“底栏最近读取”的配置版本和按钮数量；重启后再次确认设置保留。
7. 使用“恢复显示全部底栏”检查恢复效果，再测试未登录、登录、切换账号及抓包。

若列表没有出现，查看 LSPosed 日志中的 `BiliRoamingX-LSPosed`：入口加载、3/3 解析 Hook、设置目录、迁移、本地保存错误会分别记录。界面中的历史目录记录只代表记录时刻。

全部 204 项原 Settings 可按原类型保存；未移植项明确显示“仅保存”，不运行原 onChange 操作，不宣称已经生效。当前模块不从宿主读取账号资料、UID、cookie 或 accessKey；账户功能尚未移植。

配置存储于宿主 SharedPreferences `biliroamingx_lsposed_settings`。首次尝试从框架旧 Remote Preferences 迁移已知有效设置，成功后不再读取旧配置；失败时下次宿主启动重试，保留本地修改和恢复默认操作。清除哔哩哔哩数据会清除这份宿主配置。

## 构建

独立工程使用 JDK 17、Gradle 9.4.1、AGP 9.2.1、SDK 37，避免升级原 ReVanced 工程的工具链。在本目录执行：

```powershell
.\gradlew.bat assembleRelease verifyBottomBarPolicy verifySettingsRules lintRelease
python tools/check_artifact.py build/outputs/apk/release/BiliRoamingX-LSPosed-release.apk --host-only --sdk D:/Android/Sdk --report ../docs/module-artifact-check-v3.json
```

运行 `python tools/generate_settings_schema.py` 可从原 Settings.kt 重新生成 204 项配置目录，生成器遇到无法解析的声明或默认值会失败。

`verifyBottomBarPolicy` 为 4 项底栏规则测试，`verifySettingsRules` 为 6 项编辑校验和 4 项迁移测试；均是纯 JVM，不连接手机。本机 `testDebugUnitTest` 曾因测试工作进程找不到已经编译的测试类而失败，该问题尚未定位，不将其记为通过。

Release 使用本机 Android debug 证书签名，属于可安装的本地测试版。后续覆盖安装需要保留同一签名密钥；正式发布密钥尚未配置。Release 默认不输出 Debug 日志，可通过“调试日志”设置开启。

详细结果、限制和工程说明见 [首轮报告](../docs/first-round-report.md)。
