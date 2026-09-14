# 首轮宿主：官方粉版 8.27.0

用户将首轮目标从 8.14.0 改为本地提供的 8.27.0。源码基线仍为 BiliRoamingX 1.23.3，不变更到旧版漫游。

- 文件：`哔哩哔哩8.27.0原版.apk`
- 包名：`tv.danmaku.bili`
- versionName：`8.27.0`
- versionCode：`8270400`
- 文件大小：153418031 字节。
- APK SHA-256：`00ad96b15626026c9880f08c2ff3c5a8059c08eb1683928bb0e6ed2d0b37cd7c`
- 签名证书 SHA-256：`93ba270f5521139ecafe4bb638ac5b1198bc548f62d9fd8f8580a079faf5910e`
- 签名证书 MD5：`7194d531cbe7960a22007b9f6bdaa38b`，与原项目 Constants.OFFICIAL_SIG_MD5 一致。
- 证书主体：`CN=Bbcallen, OU=danmaku.tv, O=danmaku.tv, L=Zhuhai, ST=Guangdong, C=CN`。
- minSdk 23，targetSdk 34。

APK 签名验证通过。DEX 中未检出内置 `app/revanced/bilibili/` 或旧版 `me/iacn/biliroaming/` 代码标记。使用 Android SDK aapt、apksigner 和 apkanalyzer 只读分析，未修改、重签名或安装宿主 APK。

## 底栏静态定位

以下类、字段和解析重载均已从新提供的原版 APK 重新确认：

| 类 | 目标 |
| --- | --- |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$TabResponse` | `tabData`，JSONField 名称 `data` |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$TabData` | `bottom: java.util.List`；类不是 public，使用反射访问 |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$Tab` | `tabId`、`name`、`uri`，均为 String |
| `com.alibaba.fastjson.JSON` | 静态 `parseObject`，以下三个原 Patch 使用的重载存在 |

```text
parseObject(String, Class): Object
parseObject(String, java.lang.reflect.Type, Feature[]): Object
parseObject(String, java.lang.reflect.Type, int, Feature[]): Object
```

Hook 在上述解析方法返回后，使用宿主 ClassLoader 和反射适配层，调用提取为共用源码的原 1.23.3 筛选规则，保留 `showing_bottom_items` 的 `_all` 和显式 ID 集合语义。通过线程嵌套深度避免解析重载相互调用时重复过滤、丢失原始按钮目录。

模块检查包名、8.27.0 / 8270400 和上述签名，避免与重签包内置的漫游补丁叠加。包含签名检查的 Release 重建已通过。静态目标存在不等于运行调用链已经验证。手机操作、启用模块、登录与切换账号回归、抓包均由用户执行。

## 最初提供的包只保留作诊断

`哔哩哔哩_8.27.0(1).apk` 的 SHA-256 为 `ccb14af0e0e4be4de0c0238d95549a06b6a5a241c36bbc1c2cf88d7904e64baf`，签名主体为 `CN=sti-233, O=BiliRoamingX`，包含内置漫游代码。它不作为本次最终验收的官方宿主。
