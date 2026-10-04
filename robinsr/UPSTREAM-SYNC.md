# RobinSR Android 上游更新说明

本文用于以后将原始服务器仓库的更新完整迁移到 Android 分支。

## 上游记录

- Android 分支：`robinsr-android4.6.51`
- 原始仓库：`https://github.com/CAtlas0784/hkrpg-ps.git`
- 原始分支：`dev-stable`
- 当前导入提交：`45bbb7375e9c6898beed6089499924fcbc427396`
- 当前资源标记：`4.6.51-dev-stable-45bbb73`

上游服务器源码放在本仓库的 `robinsr/` 目录中。Android 工程、打包工作流和实时日志界面位于该目录之外，更新服务器源码时不要删除它们。

## 更新原则

每次更新以原始仓库 `dev-stable` 的完整内容替换 `robinsr/`，不要只挑选部分源文件。仅保留本说明文件，然后重新应用下面列出的 Android 必需适配。开始前确保工作区干净，并创建备份分支。

## 完整替换步骤

```sh
git switch robinsr-android4.6.51
git status --short
git branch "backup/robinsr-android-before-update-$(date +%Y%m%d-%H%M%S)"

git remote get-url upstream >/dev/null 2>&1 \
  && git remote set-url upstream https://github.com/CAtlas0784/hkrpg-ps.git \
  || git remote add upstream https://github.com/CAtlas0784/hkrpg-ps.git
git fetch upstream dev-stable

git log -1 --oneline upstream/dev-stable
git diff --stat 45bbb7375e9c6898beed6089499924fcbc427396..upstream/dev-stable

sync_dir="$(mktemp -d)"
git archive upstream/dev-stable | tar -x -C "$sync_dir"
test -f "$sync_dir/Cargo.toml"
rsync -a --delete --exclude UPSTREAM-SYNC.md "$sync_dir/" robinsr/
git add -A -f -- robinsr
```

`rsync --delete` 会让 `robinsr/` 与新版上游完全一致；目标只能是仓库中的 `robinsr/`，不要改成仓库根目录。`git add -f` 用于纳入上游已跟踪但被其 `.gitignore` 匹配的文件。替换完成后，将本文件顶部的“当前导入提交”和“当前资源标记”更新为新的上游提交。

## 重新应用 Android 适配

完整替换会覆盖服务器侧的 Android 修改，提交前逐项恢复并检查：

1. `robinsr/gameserver/src/lib.rs`：KCP 网关绑定 `127.0.0.1:23301`。
2. `robinsr/sdkserver/src/lib.rs`：SDK 服务绑定 `127.0.0.1:21000`。
3. `robinsr/gameserver/src/main.rs`：启动日志中的版本号与新版一致。
4. `.github/workflows/build.yml`：继续使用 `cargo ndk` 编译 `gameserver` 和 `sdkserver`，并将服务器资源复制进 APK。
5. `app/src/main/java/com/robinsr/android/ServerStorage.java`：将 `PAYLOAD_VERSION` 改为新的版本和上游短提交号，使已安装应用刷新服务器资源。
6. 更新应用版本、界面版本文字和工作流产物名称；不要移除实时日志及其关闭开关。

## APK 内置资源

工作流会在构建时从 `robinsr/` 复制下列文件，但仓库中的预置资源也应同步，方便本地检查：

```sh
cp robinsr/freesr-data.json app/src/main/assets/server/freesr-data.json
cp robinsr/persistent app/src/main/assets/server/persistent
cp robinsr/res.json app/src/main/assets/server/res.json
cp robinsr/versions.json app/src/main/assets/server/versions.json
cp robinsr/challenge_data.json app/src/main/assets/server/challenge_data.json
git add -A app/src/main/assets/server
```

`freesr-data.json` 和 `persistent` 是用户可修改数据。应用升级时 `ServerStorage` 不会覆盖用户已有文件；其他版本化资源由 `PAYLOAD_VERSION` 变化触发刷新。

## 检查与推送

```sh
git diff --check
git diff --stat

cd robinsr
cargo fmt --all -- --check
cargo check --workspace
cd ..

git commit -m "Update RobinSR Android from upstream dev-stable"
git push origin robinsr-android4.6.51
```

推送后查看 GitHub Actions 的 `Android ARM64 APK` 工作流。它会编译 ARM64 服务器、构建 APK、检查 APK 内容并上传构建产物。只有工作流全部通过后，才视为本次上游更新完成。
