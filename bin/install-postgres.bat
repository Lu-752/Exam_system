@echo off
setlocal EnableDelayedExpansion
title 安装 PostgreSQL（内置模式）

REM ---- 1) 探测已安装的 psql ----
set "PGBIN="
where psql >nul 2>&1 && for /f "delims=" %%i in ('where psql') do set "PGBIN=%%~dpi"
if not defined PGBIN (
  if exist "C:\Program Files\PostgreSQL\17\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\17\bin\"
  if exist "C:\Program Files\PostgreSQL\16\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\16\bin\"
  if exist "C:\Program Files\PostgreSQL\15\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\15\bin\"
  if exist "C:\Program Files\PostgreSQL\14\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\14\bin\"
)

REM ---- 2) 未安装则尝试自动安装 ----
if not defined PGBIN (
  echo [安装] 未检测到 PostgreSQL，尝试用 winget 安装（需联网）...
  where winget >nul 2>&1 && winget install -e --id PostgreSQL.PostgreSQL --silent --accept-package-agreements --accept-license-agreements
  if exist "C:\Program Files\PostgreSQL\17\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\17\bin\"
  if exist "C:\Program Files\PostgreSQL\16\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\16\bin\"
)
if not defined PGBIN (
  echo [安装] winget 失败，尝试 chocolatey...
  where choco >nul 2>&1 && choco install postgresql -y
  if exist "C:\Program Files\PostgreSQL\16\bin\psql.exe" set "PGBIN=C:\Program Files\PostgreSQL\16\bin\"
)
if not defined PGBIN (
  echo.
  echo [失败] 无法自动安装 PostgreSQL。请手动安装后，用启动器“模式2”连接。
  echo   下载：https://www.postgresql.org/download/windows/
  pause
  exit /b 1
)

set "PATH=%PGBIN%;%PATH%"
set "PGUSER=exam"
set "PGDB=exam_sys"
set "PGPASS=exam"

REM ---- 3) 启动服务 ----
net start postgresql-x64-17 >nul 2>&1
net start postgresql-x64-16 >nul 2>&1
net start postgresql-x64-15 >nul 2>&1
net start postgresql-17 >nul 2>&1
net start postgresql-16 >nul 2>&1
net start postgresql-15 >nul 2>&1

REM ---- 4) 建角色 / 建库（如已存在则跳过）----
echo [建库] 创建角色 exam 与数据库 exam_sys ...
psql -U postgres -h 127.0.0.1 -tc "SELECT 1 FROM pg_roles WHERE rolname='exam'" | findstr 1 >nul || psql -U postgres -h 127.0.0.1 -c "CREATE ROLE exam LOGIN PASSWORD 'exam';"
psql -U postgres -h 127.0.0.1 -tc "SELECT 1 FROM pg_database WHERE datname='exam_sys'" | findstr 1 >nul || psql -U postgres -h 127.0.0.1 -c "CREATE DATABASE exam_sys OWNER exam;"
psql -U postgres -h 127.0.0.1 -c "GRANT ALL PRIVILEGES ON DATABASE exam_sys TO exam;" >nul 2>&1
echo [完成] PostgreSQL 就绪：库=exam_sys 用户=exam 密码=exam
echo   注：建库时需输入 postgres 超级用户密码；可在本窗口先执行  set PGPASSWORD=你的密码  再运行。
pause
exit /b 0
