# 第一轮实际结果

已生成面向官方粉版 8.27.0 的独立 Modern API 102 模块测试 APK。已实现底栏隐藏和设置通信代码，完成构建与静态检查；**尚未进行手机端 Hook、跨进程实际回传或界面效果验证**。这些由用户执行，不把静态成功记为真机成功。

## 完成情况

| 项目 | 实际结果 |
| --- | --- |
| 1.23.3 源码与 r2049 基线 | 完成；91 个 Patch 名称一致，237 个 Patch JVM 类字节码一致 |
| 原架构构建 | 原始、去黑名单及共用规则接入后的 dist 均通过 |
| 客户端 Kill Switch | 删除且独立提交；修复后 APK/JAR 指定标记扫描无残留 |
| 独立模块 | Release 构建、v2 签名验证、入口/作用域/API 102 元数据检查通过；管理器实际识别待手机确认 |
| 首个功能 | Json 的底部导航过滤已实现；原版 8.27.0 类/字段/重载静态定位通过；4 项规则测试通过 |
| 配置 | 原 204 项 Settings 的类型、默认值和依赖元数据已提取；通过框架 Remote Preferences 保存和读取；真机通信待验 |
| APK 检查 | 无 legacy 入口、无打包 API 实现、无宿主 dummy 类、无嵌套 integrations APK / patches JAR；详见 module-artifact-check.json |
| 静态检查 | lintRelease：0 errors、27 warnings。包括私有 attach 反射、导出的目录 Provider、工具版本和资源/本地化建议，未全部消除 |

基线细节见 [baseline-verification.md](baseline-verification.md)，宿主签名及静态定位见 [host-8.27.0.md](host-8.27.0.md)。

## 工程和修改文件

```text
integrations/app/                 原 integrations，去除黑名单并接入共用规则
integrations/runtime/             从原 JSONPatch 提取的 BottomBarPolicy
patches/                         保留原静态 Patch；只清除黑名单独占资源
xposed/                          独立 Android Application / 独立构建工具链
  src/main/resources/META-INF/xposed/   Modern API 102 入口、属性、作用域
  src/main/java/.../ModuleEntry         包名/版本/签名检查，Application.attach 初始化
  src/main/java/.../HostRuntime         宿主 Context、ClassLoader、Activity 生命周期
  src/main/java/.../BottomBarHook       JSON after Hook 和反射适配
  src/main/java/.../ModuleApplication   模块侧 Remote Preferences 服务与保存
  src/main/java/.../SettingsActivity    204 项独立设置与底栏选择
  src/main/java/.../CatalogProvider     UID 校验的导航目录/诊断回传
  src/main/assets/settings-schema.json 原设置目录
  tools/、src/test/                     生成器、产物扫描和共用规则测试
docs/                            基线、迁移清单、验证结果及本报告
```

原文件修改限于 Accounts.kt、Setting.kt、BlacklistInfo.kt 删除、host/values/strings.xml 清理、JSONPatch.java 共用规则委托、integrations/app/build.gradle.kts 源码目录，以及根 gradle.properties 的中文路径配置。完整黑名单 diff 见 blacklist-removal.diff，单独提交为 `5261a8c`。

## Hook 与初始化

ModuleEntry 继承 XposedModule，使用无参入口。仅粉版主进程、8.27.0 / 8270400 且匹配官方签名时启用。Application.attach 原方法先执行一次，再建立宿主运行环境；注册 ActivityLifecycleCallbacks，以弱引用跟踪前台 Activity。

JSON 的三个已核对 parseObject 重载在返回后处理 TabResponse（也支持 GeneralResponse.data 包装）。反射访问非 public 的 TabData，调用共用 BottomBarPolicy 保留原 `_all` / 显式 ID 规则。重载嵌套只处理最外层返回，避免先过滤后丢失完整目录。新列表准备完成后才写回；配置与当前 ID 完全不匹配时保留原导航并记录错误、回传新目录，不留下空导航。

原方法异常继续传播；模块自身适配异常记录 Hook、宿主版本、类、方法及原因，并隔离在对应功能内。不是用捕获异常后再次调用原方法来兜底。

## Context 与通信

hostContext 是 Bilibili Application，模块设置界面和目录缓存使用模块自己的 Context。没有让宿主直接读取模块 dataDir 或复用原 CrossProcessPreferences。

配置由模块侧 libxposed service 写入 Remote Preferences，宿主侧使用 XposedModule.getRemotePreferences 读取。一份配置快照同时提供选择集合和配置版本，避免回传版本与读取值不一致。

另有单向目录 Provider，仅传实际底栏 ID、名称、配置版本和数量，不传 UID/账号资料，也不传设置值。Provider 的 call 校验 Binder 调用 UID 必须属于粉版或模块自身；其余 CRUD 入口拒绝访问。这解释了 lint 的导出 Provider 提示；实际跨 UID 可达性仍待手机验证。

底栏功能不依赖账号。原 Accounts 的正常逻辑保留在 ReVanced 工程中，尚未整体接入独立模块，也未在独立模块注册账号变化 Receiver。未来移植账户相关功能时，仍需迁移账户初始化、广播及缓存失效流程，不能据此宣称 ApplicationDelegate 全部职责已迁移。

## 黑名单与限制

删除请求、延迟触发、封禁缓存/字段、全局 Setting 禁用条件及独占模型/资源；StringDecoder 因仍有其他用途而保留。正常解析、字幕翻译、更新代码未删除，服务器 ACL 未修改。扫描通过不等于抓包通过，手机无黑名单请求仍待用户确认。

独立模块未移植推荐、评论、动态、播放器、字幕、解析、UI 嵌入等其他功能。设置保存不等于功能移植；未移植项不执行原 onChange 回调。LSPosed 管理器识别、宿主不崩、底栏效果、跨进程持久化、未登录、账号切换、抓包均待手机验证。

`testDebugUnitTest` 的本机测试工作进程类加载问题未解决；可复现的 `verifyBottomBarPolicy` 任务和直接 JUnit 执行均为 4/4 通过。没有运行手机测试，也没有操作设备。

## 下一阶段候选（均未实现）

| Patch | 下一步 |
| --- | --- |
| Json 的其余首页子功能 | 先验证当前底栏，再逐项增加顶部入口与首页标签过滤 |
| Teenager mode | 在原版 8.27.0 定位弹窗触发方法，转换提前返回逻辑 |
| Share | 复用分享净化逻辑，确认分享数据与 Context 入口 |
| Default playback speed | 定位播放器读取默认速度的位置，恢复配置依赖 |
| Fake not in multi window mode | 评估窗口状态查询与回调的运行时对应关系 |

全部 91 项仍逐项记录在 [patch-port-status.md](patch-port-status.md)。本轮只计为 **Json 的一个子功能已实现**，不计为完整 Json 或 91/91 完成。

## 交付与验证

交付目录为 `X:\临时同步\BiliRoamingX-LSPosed-first-round`。顶层带 `LSPosed` 的 APK 是本轮测试模块；baseline-rebuilt、blacklist-removed、initial-build 等子目录是构建阶段留档。原架构 integrations APK 不是独立模块。

手机操作顺序见 [xposed/README.md](../xposed/README.md)。最终 APK 的 SHA-256 与大小见 [module-artifact-check.json](module-artifact-check.json)，网络盘复制后逐一核对哈希。GPL-3.0 和原版权信息保留；源代码及本轮修改均在当前工程中。
