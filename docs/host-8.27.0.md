# 首轮宿主：粉版 8.27.0

用户将首轮目标从 8.14.0 改为本地提供的 8.27.0。源码基线仍为 BiliRoamingX 1.23.3，不变更到旧版漫游。

- 文件：`哔哩哔哩_8.27.0(1).apk`
- 包名：`tv.danmaku.bili`
- versionName：`8.27.0`
- versionCode：`8270400`
- SHA-256：`ccb14af0e0e4be4de0c0238d95549a06b6a5a241c36bbc1c2cf88d7904e64baf`
- minSdk 23，targetSdk 34。

使用 Android SDK aapt 和 apkanalyzer 对 APK 做只读分析，未修改宿主 APK。包名和版本不能代替官方签名认证，签名来源尚未独立核实。

## 底栏静态定位

以下类和字段均已从这份 APK 的 DEX 确认：

| 类 | 目标 |
| --- | --- |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$TabResponse` | `tabData`，JSONField 名称 `data` |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$TabData` | `bottom: java.util.List` |
| `tv.danmaku.bili.ui.main2.resource.MainResourceManager$Tab` | `tabId`、`name`、`uri`，均为 String |
| `com.alibaba.fastjson.JSON` | 静态 `parseObject`，以下三个原 Patch 使用的重载存在 |

```text
parseObject(String, Class): Object
parseObject(String, java.lang.reflect.Type, Feature[]): Object
parseObject(String, java.lang.reflect.Type, int, Feature[]): Object
```

原 Json Patch 的底栏过滤可在上述解析方法返回之后，针对 TabResponse 数据进行转换。需保留宿主 ClassLoader 与反射适配层，复用原 `showing_bottom_items` 的 `_all` 和显式 ID 集合语义。类及方法存在不等于运行调用链已验证。

当前状态：静态目标已确认；Hook 尚未完成，编译和真机效果待验证。手机操作、启用模块、登录与切换账号回归、抓包均由用户执行。
