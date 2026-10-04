# RobinSR Android 4.6.51

Android ARM64 launcher for the `dev-stable` 4.6.51 server core from
[CAtlas0784/hkrpg-ps](https://github.com/CAtlas0784/hkrpg-ps/tree/dev-stable).

The app starts the SDK server on TCP `127.0.0.1:21000` and the game server on
UDP `127.0.0.1:23301`, provides live process logs, and includes an embedded
SRTools view. Server resources are installed under `Download/RobinSR`.

The GitHub Actions workflow cross-compiles both Rust servers for Android
ARM64, packages the 4.6.51 resources, builds the APK, and verifies its contents.

This project includes GPLv3 server code. See `robinsr/LICENSE` and the upstream
attribution in `robinsr/README.md`.
