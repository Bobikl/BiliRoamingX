# 删除客户端远程黑名单

修改范围限定于客户端全局 Kill Switch：

- Accounts 删除远程状态字段、延迟检查触发、请求与缓存、封禁和解禁提示。
- Setting.get() 只保留正常的设置依赖判断。
- 删除仅该检查使用的 BlacklistInfo，以及五条独占字符串资源。

删除前已经核对引用。StringDecoder 仍被 CommentChecker 使用，因此保留。正常账号读取、UID、accessKey、cookie、VIP 状态及账号广播处理均保留；未修改番剧解析、字幕翻译、更新等网络功能，也未更改服务器端 ACL。

已对 integrations/patches 的源文件扫描指定字段、缓存前缀、方法、编码地址和域名，未再发现残留。原始参照产物与 Git 历史故意保留，用于比对，不属于修复后的可执行源码。

删除后的 `dist` 已成功，修复后 integrations APK 和 patches JAR 的全部解压条目经 UTF-8 / UTF-16LE 标记扫描无命中；同一检查能在原基线 APK 中检出原有标记，确认检查有效。独立模块的产物检查见 module-artifact-check.json。手机运行和抓包由用户执行，不能把源码扫描等同于运行验证。
