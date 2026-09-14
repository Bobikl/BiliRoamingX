# 1.23.3 / r2049 基线核对

源码来自 Colorful-glassblock/BiliRoamingX-Builds 的 tag `1.23.3`，原提交 `0f2e66b56c20ddd2caeb92df2daddc354b3cca40`。Dobby 子模块固定为 `6813ca76ddeafcaece525bf8c6cde7ff4c21d3ce`。GPL-3.0 与原版权信息保留。

## 构建结果

2026-09-07，Windows、JDK 17.0.6、补丁工具链 JDK 11、Gradle 8.10.2、AGP 8.6.1、NDK 26.3.11579264 环境下，原业务源码 `dist` 成功。

必要的环境调整是 `android.overridePathCheck=true`，允许当前中文工作目录。原生工具部分输出仍受其自身终端编码影响；源文件使用 UTF-8。构建前仅增加文档和路径配置，未改业务代码。

GitHub Packages 的 `kofua.app.revanced:revanced-patcher:19.3.1` 需要认证。本次从原维护者公开的 [CLI v4.6.0](https://github.com/zjns/revanced-cli/releases/tag/v4.6.0) 恢复构建依赖：该 tag 的版本目录声明 patcher 19.3.1，JAR 内 `app/revanced/patcher/version.properties` 也确认为 19.3.1，apktool 为 2.9.3。CLI SHA-256：`619a308fda48da0499ecc24507673ecda71e1e3acc684fc0992a951cb495a17a`。

恢复过程保留原 patcher 类字节码和 Kotlin 元数据，另存构建支持依赖并重建本地 POM；这不是私有 Maven 原文件的逐字节复原。构建通过 `-Dmaven.repo.local=.../tools/maven` 使用本地仓库。恢复脚本、来源及哈希记录在工作区 `tools/restore_patcher_dependency.py` 与 `reference/dependency-recovery.json`，不打入独立模块 APK。

## 结构比较

| 检查 | r2049 | 本次原版重建 | 结果 |
| --- | --- | --- | --- |
| integrations 类列表 | 757 | 757 | 完全一致 |
| integrations 类内方法引用签名 | 3084 | 3084 | 完全一致；不等于逐方法行为证明 |
| integrations ZIP 条目 | 9 | 9 | 条目一致 |
| resources.arsc | 40 字节 | 40 字节 | 字节完全一致 |
| Patch 数量与名称 | 91 | 91 | 完全一致 |
| patches JVM 类 | 237 | 237 | 类列表及全部类字节码完全一致 |

重建 integrations APK 为 476533 字节，SHA-256 `3eeb86a7bd10c2691dad0facf06e9fcb965e092536c421f346344130861f73d5`。重建 patches JAR 为 1266505 字节。

二进制不完全一致：版本字符串没有 `.r2049` 后缀，Git 版本记录不同，原生库由本地工具链重建，DEX/JAR 打包与 D8 环境也可能不同。mapping.txt 字节大小同为 5787213，但内容不完全一致。`patches.json` 只有 `Spoof SIM country` 的选项发生差异：本机默认语言使国家显示名变为中文；Patch 类字节码未变化。

原版重建四件套已留在工作区 `reference/baseline-rebuilt`，r2049 在 `reference/r2049`。这些为 ReVanced 原架构比对产物，尚未删除黑名单，不能当独立 LSPosed 模块。

没有操作手机，也没有做启动、登录、账号切换或抓包验证。
