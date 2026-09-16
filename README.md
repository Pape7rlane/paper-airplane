# Pearl SR

首次安装后打开应用，完成卡密验证并授予存储权限，程序会自动准备以下目录：

```text
/storage/emulated/0/Download/Pearl SR/
├── resources/
├── freesr-data.json
├── hotfix.json
└── Strools/
    ├── freesr-data.json  （从 SRTools 下载后出现）
    └── config.json       （从 SRTools 下载后出现）
```

- Android 11 及以上需要在系统设置中允许“所有文件访问权限”；Android 10 及以下需要存储读写权限。未授权时不启动服务端。
- 资源在首次打开应用并授权后复制，安装 APK 本身不会执行应用代码。后续打开只补齐缺失文件，保留已有同步数据和自定义资源；旧版私有目录中已有的 JSON 会在公共目录缺失时迁移。
- 先启动服务端，再打开 SRTools。Connect PS 选择 **RobinSR**，地址填写 **http://localhost:21000**。点击 **Sync** 通过 `/srtools` 将数据写入根目录的 `freesr-data.json`，并由 Dispatch 通知 GameServer 重新加载；重载失败时可在游戏内执行 `/sync` 或重启服务端。
- 网页上的下载按钮是独立操作。下载的 `freesr-data.json` 和 `config.json` 保存到 `Strools/`，不会替换根目录的服务端数据。HTTP 和 Blob 下载均使用此规则。

内置 SRTools 页面会检查本地 Sync 响应中的业务状态，服务端写入失败时显示错误，避免 HTTP 200 导致误报同步成功。

应用不再提供日志按钮和日志面板；服务端标准输出与错误输出丢弃到 `/dev/null`，不再读取或累计到界面。启动和更新失败仍显示状态或提示。

云编译配置使用 Java 17、Gradle 8.6、AGP 8.4.0 和 Android SDK 34，与当前依赖匹配。构建后会检查 APK 中的原生服务端和所有 assets，包含 `srtools-sync.js`。当前 release 配置没有签名密钥，工作流生成的 release APK 仍需使用自己的固定密钥签名后才能安装及覆盖更新。
