# Upstream Sync

The Android 4.6.51 server core is synchronized from
`https://github.com/CAtlas0784/hkrpg-ps.git`, branch `dev-stable`.
The imported upstream revision is `45bbb7375e9c6898beed6089499924fcbc427396`.

## Update procedure

1. Add or update the upstream remote:

   ```sh
   git remote add upstream https://github.com/CAtlas0784/hkrpg-ps.git
   git fetch upstream dev-stable
   ```

2. Review the upstream commits and changes, especially `versions.json`,
   `freesr-data.json`, protocol files, and server configuration.

3. Merge or rebase the reviewed changes onto `robinsr`, resolve conflicts,
   and run the Rust checks/build locally:

   ```sh
   cargo fmt --all -- --check
   cargo check --workspace
   cargo build --release --bin gameserver --bin sdkserver
   ```

4. Reapply the Android-specific localhost binding and verify
   `challenge_data.json` is included in the APK assets, then push the reviewed result:

   ```sh
   git push origin robinsr-android4.6.51
   ```

The GitHub workflow is `.github/workflows/prebuilt.yml`. It builds the Windows
prebuilt ZIP on pushes to `main` or `robinsr`, or by manual dispatch, uploads it as an Actions
artifact, and publishes it as a GitHub Release asset only for version-tag pushes.
Do not copy the old `.gitea` workflow back: it uses Gitea-only variables and APIs.
