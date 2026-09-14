# 哔哩漫游X：首轮 LSPosed 移植

基于 BiliRoamingX 1.23.3 / GPL-3.0。第一轮只实现 Json Patch 的底部导航过滤，不是全部 91 个 Patch 的完整移植。

要求支持 Modern API 102 的 LSPosed，宿主为官方粉版 8.27.0（8270400）。模块自身 minSdk 26。宿主包名、版本和签名均有检查。

## 手机验证步骤（由用户执行）

1. 安装交付目录顶层的 `BiliRoamingX-LSPosed-1.23.3-bili8.27.0-test.apk`。
2. 在 LSPosed 启用模块，作用域勾选粉版哔哩哔哩。
3. 打开独立模块设置，确认“已连接 LSPosed”。
4. 彻底关闭并重新打开哔哩哔哩，进入首页，再返回模块设置读取实际底栏列表。
5. 取消一个底栏按钮的勾选并保存，再彻底关闭并重新打开哔哩哔哩，检查按钮是否隐藏。
6. 返回模块设置，核对“宿主最近读取”的配置版本和按钮数量；重启后再次确认设置保留。
7. 使用“恢复显示全部底栏”检查恢复效果，再测试未登录、登录、切换账号及抓包。

若列表没有出现，查看 LSPosed 日志中的 `BiliRoamingX-LSPosed`：入口加载、3/3 解析 Hook、兼容性错误和目录回传错误会分别记录。设置界面的历史回传记录只代表记录时刻，不代表当前进程仍在运行。

全部 204 项原 Settings 可按原类型保存；未移植项明确显示“仅保存”，不运行原 onChange 操作，不宣称已经生效。当前模块不读取账号、UID、cookie 或 accessKey；相关账户功能尚未移植。

## 构建

独立工程使用 JDK 17、Gradle 9.4.1、AGP 9.2.1、SDK 37，避免升级原 ReVanced 工程的工具链。在本目录执行：

```powershell
.\gradlew.bat assembleRelease verifyBottomBarPolicy lintRelease
python tools/check_artifact.py build/outputs/apk/release/BiliRoamingX-LSPosed-release.apk --report ../docs/module-artifact-check.json
```

运行 `python tools/generate_settings_schema.py` 可从原 Settings.kt 重新生成 204 项配置目录，生成器遇到无法解析的声明或默认值会失败。

`verifyBottomBarPolicy` 是纯 JVM 的 4 项规则测试，不连接手机。本机 `testDebugUnitTest` 曾因测试工作进程找不到已经编译的测试类而失败；独立 JVM 任务和直接 JUnit 执行均通过，Android 测试工作进程问题尚未定位，不将其记为通过。

Release 使用本机 Android debug 证书签名，属于可安装的本地测试版。后续覆盖安装需要保留同一签名密钥；正式发布密钥尚未配置。Release 默认不输出 Debug 日志，可通过“调试日志”设置开启。

详细结果、限制和工程说明见 [首轮报告](../docs/first-round-report.md)。
