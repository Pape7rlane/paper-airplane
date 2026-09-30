# Upstream Sync

This repository is a GitHub mirror of `https://git.neonteam.dev/amizing/robinsr.git`.
The mirror branch is `robinsr`; the upstream branch is `main`.

## Update procedure

1. Add or update the upstream remote:

   ```sh
   git remote add upstream https://git.neonteam.dev/amizing/robinsr.git
   git fetch upstream main
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

4. Push the reviewed result to GitHub:

   ```sh
   git push origin robinsr
   ```

The GitHub workflow is `.github/workflows/prebuilt.yml`. It builds the Windows
prebuilt ZIP on pushes to `main` or `robinsr`, or by manual dispatch, uploads it as an Actions
artifact, and publishes it as a GitHub Release asset only for version-tag pushes.
Do not copy the old `.gitea` workflow back: it uses Gitea-only variables and APIs.
