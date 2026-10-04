@echo off
cd /d "%~dp0"

echo ==============================================
echo   Starting Hoyo-hkrpg-PS Servers...
echo ==============================================

if exist "sdkserver.exe" goto launch
if exist "target\release\sdkserver.exe" goto launch

echo [!] Executables not found. Compiling with cargo build --release...
call cargo build --release
if not exist "target\release\sdkserver.exe" (
    echo [X] Build failed!
    pause
    exit /b 1
)

:launch
echo [*] Launching SDK Server (:21000)...
if exist "sdkserver.exe" (
    start "SDK Server" cmd /k "sdkserver.exe"
) else (
    start "SDK Server" cmd /k "target\release\sdkserver.exe"
)

ping 127.0.0.1 -n 3 > nul

echo [*] Launching Game Server (:23301)...
if exist "gameserver.exe" (
    start "Game Server" cmd /k "gameserver.exe"
) else (
    start "Game Server" cmd /k "target\release\gameserver.exe"
)

echo.
echo ==============================================
echo [OK] Both servers launched in separate windows!
echo Keep this window open or press any key to close.
echo ==============================================
pause