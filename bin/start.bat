@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
title 考试系统 Exam System 启动器

REM ---- 参数：-d 后台运行 / --port=9090 指定端口 ----
set "PORT=8080"
set "BG=0"
for %%a in (%*) do (
  set "A=%%~a"
  if /i "!A!"=="-d" set "BG=1"
  if /i "!A:~0,7!"=="--port=" set "PORT=!A:~7!"
)

REM ---- 定位 jar ----
set "JAR="
if exist "%cd%\exam-server-0.1.0.jar" set "JAR=%cd%\exam-server-0.1.0.jar"
if not defined JAR if exist "%cd%\server\target\exam-server-0.1.0.jar" set "JAR=%cd%\server\target\exam-server-0.1.0.jar"
if not defined JAR (
  echo [错误] 未找到 exam-server-0.1.0.jar
  echo   请从 release 目录启动，或先执行 scripts\build_release.py 完成打包。
  pause
  exit /b 1
)

REM ---- 优先使用包内自带运行时（无需安装 Java）----
set "JAVA="
set "JAVAW="
if exist "%cd%\runtime\bin\java.exe" set "JAVA=%cd%\runtime\bin\java.exe"
if exist "%cd%\runtime\bin\javaw.exe" set "JAVAW=%cd%\runtime\bin\javaw.exe"
if not defined JAVA (
  where java >nul 2>&1
  if errorlevel 1 (
    echo [错误] 未找到可用的 Java：
    echo   - 本包应自带 runtime\bin\java.exe，若缺失说明压缩包不完整；
    echo   - 或安装 JDK 21 及以上版本并加入 PATH。
    pause
    exit /b 1
  )
  set "JAVA=java"
  set "JAVAW=javaw"
)

echo ============================================================
echo            考试系统 - 启动器
echo ============================================================
echo   安装方式：
echo     1  内置演示库 - 开箱即用，零安装，内嵌 H2 数据库
echo     2  连接已有 PostgreSQL - 表由 Flyway 自动创建
echo     3  内置 PostgreSQL - 本机未装时由脚本自动安装并建库
echo ------------------------------------------------------------
echo   端口 %PORT%  运行方式：%BG% (0=前台 1=后台)
echo   参数：-d 后台运行   --port=9090 换端口
echo ============================================================
set "MODE=1"
set /p INPUT=请选择安装方式 [1/2/3，回车默认1]：
if defined INPUT set "MODE=%INPUT%"

if "%MODE%"=="1" goto :MODE1
if "%MODE%"=="2" goto :MODE2
if "%MODE%"=="3" goto :MODE3
echo 输入无效，使用默认模式 1。
goto :MODE1

:MODE1
set "ARGS=--server.port=%PORT% --spring.profiles.active=dev"
echo.
echo [模式1] 内置演示库，数据写入 .\data\exam.mv.db
call :RUN
goto :eof

:MODE2
echo.
set "PGHOST=127.0.0.1"
set "PGPORT=5432"
set "PGDB=exam_sys"
set "PGUSER=exam"
set "PGPASS=exam"
set /p PGHOST=PostgreSQL 主机 [默认 127.0.0.1]：
set /p PGPORT=端口 [默认 5432]：
set /p PGDB=数据库名 [默认 exam_sys]：
set /p PGUSER=用户名 [默认 exam]：
set /p PGPASS=密码 [默认 exam]：
set "ARGS=--server.port=%PORT% --spring.profiles.active=pg --DB_URL=jdbc:postgresql://%PGHOST%:%PGPORT%/%PGDB% --DB_USER=%PGUSER% --DB_PASSWORD=%PGPASS%"
echo [模式2] 连接已有 PostgreSQL: %PGHOST%:%PGPORT%/%PGDB%  - 表由 Flyway 自动创建
call :RUN
goto :eof

:MODE3
echo.
echo [模式3] 检测 / 自动安装 PostgreSQL ...
call "%~dp0install-postgres.bat"
if errorlevel 1 (
  echo PostgreSQL 安装未成功，请查看上方提示，或手动安装后改用模式2。
  pause
  exit /b 1
)
set "ARGS=--server.port=%PORT% --spring.profiles.active=pg --DB_URL=jdbc:postgresql://127.0.0.1:5432/exam_sys --DB_USER=exam --DB_PASSWORD=exam"
echo [模式3] 启动: exam_sys / exam / exam
call :RUN
goto :eof

:RUN
if not exist "%cd%\logs" mkdir "%cd%\logs"
> "%cd%\logs\app.port" echo %PORT%
if "%BG%"=="1" (
  echo   后台启动中，日志：logs\app.log
  start "ExamSystem" "%JAVAW%" -jar "%JAR%" %ARGS% --logging.file.name=logs/app.log
  echo.
  echo   已提交后台启动，约 10~20 秒后可访问：http://localhost:%PORT%
  echo   停止：bin\stop.bat
  ping -n 4 127.0.0.1 >nul
  exit /b 0
)
echo   启动后浏览器打开 http://localhost:%PORT%  （Ctrl+C 停止）
echo ------------------------------------------------------------
"%JAVA%" -jar "%JAR%" %ARGS%
echo.
echo [已退出] 若非手动停止，请把上面的报错信息发给管理员。
pause
exit /b 0
