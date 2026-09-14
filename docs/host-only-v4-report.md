# v4：原版设置目录与同类 JSON 功能

版本：`1.23.3-lsposed.4`。宿主仍是官方粉版 8.27.0 / 8270400，签名校验不变。用户已确认 v3 生效；本版新增内容只有构建、静态检查和 JVM 测试结果，未代替用户做手机操作或抓包。

## 设置界面

- 只保留 App 内入口，无独立模块设置页、组件或跨 UID Provider。
- `settings-pages.json` 从原版 XML、字符串、数组和 Kotlin 页面引用生成，保留主目录与子目录、标题、说明、选项顺序。49 页覆盖全部 204 项 Settings，并验证从根目录可到达。
- 用哔哩哔哩风格的列表、分组间隔、标题栏、粉色开关和勾选控件替换旧版原生 Button 列表，区分布尔开关与多选项目，适配深色、字体缩放和窗口安全区。
- 未移植项控件替换为“未移植”，不可点击修改。目录项仍可进入查看子功能。未移植的原版动作（备份、导入、日志分享等）也不可操作。
- 原 Kotlin 自建页面使用其 Settings 引用生成展示行；16 个不在原 XML 中的内部值列于“其他配置”。没有恢复原版所有动态 widget 的布局或宿主 Garb 皮肤资源，因此不宣称像素级一致；真机视觉仍待检查。
- 底栏与“我的”选项来自宿主响应，点击自动保存，保留 `_all` 语义。默认不启用新增功能，已有旧配置按已保存的值使用。

## 已接入范围

包括底栏与调试日志共 16 个配置键；下表仅描述本版处理的范围。

| 页面 / 配置 | 实现 |
| --- | --- |
| 首页底栏 `showing_bottom_items` | 原版规则、宿主目录和配置确认记录；用户此前已验证底栏隐藏 |
| 首页标签 `customize_home_tab` | 按原版 URI 分类隐藏直播、推荐、热门、番剧、电影、韩综和其他标签；不允许过滤后没有任何标签 |
| 游戏入口 `purify_game` | 过滤首页 top 游戏入口；原版 EventEntranceModel 结果置空分支 |
| 首页竖屏入口 `disable_main_page_story` | 仅过滤 topLeftInfo 中 videoshortcut 入口，其他 Protobuf 用户偏好处理未移植 |
| 推荐模式引导 `block_recommend_guidance` | 原版 RecommendModeGuidanceConfig 结果置空分支 |
| 我的项目 `showing_drawer_items` | 处理 sectionListV2 标题、菜单和按钮；保存过滤前的名称 / ID 目录；设置项目及其所在分组始终保留 |
| 我的红点 `purify_drawer_reddot` | 清除菜单 item.redDot / redDotRorNew；其他来源提示不在此范围 |
| 我的提示 `block_tips` | 清空 AccountMine.liveTip / gameTips |
| 空间净化 `customize_space` | 原版空间 tab、直播入口、广告、充电、守护及作品/推广模块字段过滤，处理 buttonEntranceList；全隐藏标签时保留原标签 |
| 开屏净化 `purify_splash` | SplashData、SplashShowData、BrandSplashData 和 EventSplashDataList；保留生日内容，不清理本地已有缓存，也不移植原 onChange 缓存操作 |
| 直播弹窗 `purify_live_popups` | 购物卡片、关注提醒、直播预约、投喂支持、滚动横幅、电池任务、正在去买、主播帮玩、各种 +1、心愿助力、直播效果打分共 11 项 |
| 直播蒙层 `remove_live_mask` | 清空 roomInfo.areaMaskInfo |
| 直播水印 `remove_live_watermark` | roomInfo.newSwitchInfo 的 room-player-watermark 置 0，保留其他键 |
| 直播屏蔽信息 `live_no_block` | 原版 roomInfo.blockInfo 清空分支 |
| UP 推荐广告 `block_up_rcmd_ads` | 仅清空 DmAdvert.ads；原 HTTP / 其他字节码路径未移植，界面注明范围 |
| 调试日志 `debug` | 原模块日志控制 |

直播“购物精选”没有在本次移植的原 JSON 分支中实现；“礼物星球”原用字段 `liveGiftStarPendantInfo` 在该官方宿主类中不存在。两项均显示“未移植”，不会因为整个集合可编辑就出现可用开关。其他 188 个配置键未启用编辑。

主页加番剧/影视标签、抽屉 UI、推荐流、动态、评论、搜索、播放、字幕等其他功能仍未移植。没有把整个 Json Patch 或 91 个 Patch 标记为完成。

## 实现与验证

`BottomBarHook` 保留原三个解析 Hook 和线程嵌套保护；新 `JsonFeatureFilter` 只处理已核对的响应类型。原方法照常执行一次；后处理失败记录 `Json.Features` 错误并返回原解析对象，字段更新可能部分完成。没有吞掉原解析方法的异常。零项导航兜底与设置入口保留是额外防护。

官方宿主 SHA-256 为 `00ad96b15626026c9880f08c2ff3c5a8059c08eb1683928bb0e6ed2d0b37cd7c`。官方包 Tab 路由字段是 `uri`，没有使用诊断重签包的字段假设。

- `verify_json_host.py`：32 类、74 个成员检查通过，含私有 Kotlin 字段和生日判断方法。见 `json-host-check-v4.json`。
- JVM：底栏 4 项、编辑/迁移 10 项、JSON 12 项，共 26 项通过。覆盖默认首页、不变列表、全隐藏兜底、设置入口、完整目录、空间关联入口、生日保留、空数据、直播水印和结果置空语义。
- Release 构建与 R8 完成；lint 为 0 errors / 26 warnings（包括版本建议、既有反射提示及绘制分配提示）。最终包与目录、组件、禁止内容检查记录在 `module-artifact-check-v4.json`。
- APK 68,196 字节，SHA-256 为 `385f2136e5c00bbaf8f03c7e3d1e6f7cc6db1054807efbf8bb2f2b0cdcfddc31`；v2 签名验证通过，证书 SHA-256 与前版相同：`9ac1989e811dca262387317264b7ecfc72535278c2c6af0cf44b40344811db12`。
- 未运行设备端 UI 测试、网络触发测试、登录回归或抓包。类存在不代表所有响应会走这三个解析入口，也不保证缓存内容立即更新。

## 用户验证顺序

覆盖安装 v4，彻底关闭再打开哔哩哔哩；进入首页和“我的”刷新目录，再到 App 设置打开哔哩漫游X。先验证页面分类、未移植文字、返回和深色模式，再逐个开启首页 / 我的 / 空间 / 直播功能并重启检查。开屏可能受已有缓存影响。最后检查登录、切换账号和重启后的设置保留。
