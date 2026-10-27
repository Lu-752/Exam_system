@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
title 考试系统 停止器

set "PORT=%~1"
if not defined PORT if exist "%cd%\logs\app.port" (
  for /f "usebackq delims=" %%p in ("%cd%\logs\app.port") do set "PORT=%%p"
)
if not defined PORT set "PORT=8080"

echo ============================================================
echo   停止端口 %PORT% 上的考试系统
echo ============================================================

set "FOUND="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
  for /f "tokens=1" %%n in ('tasklist /FI "PID eq %%p" /NH 2^>nul') do (
    if /i "%%n"=="java.exe" (
      set "FOUND=1"
      echo   结束 java 进程 PID=%%p
      taskkill /F /PID %%p >nul 2>&1
    )
    if /i "%%n"=="javaw.exe" (
      set "FOUND=1"
      echo   结束 javaw 进程 PID=%%p
      taskkill /F /PID %%p >nul 2>&1
    )
  )
)

if not defined FOUND (
  echo   未发现在 %PORT% 端口监听的 Java 进程（可能已停止）。
  echo   如需强行按名字结束：taskkill /F /IM javaw.exe
) else (
  echo   已停止。
)
del /q "%cd%\logs\app.port" >nul 2>&1
pause
