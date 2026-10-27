@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."
title 考试系统 完整性校验

if not exist "SHA256SUMS.txt" (
  echo [错误] 未找到 SHA256SUMS.txt，请确认使用完整的发行包。
  pause
  exit /b 1
)

echo ============================================================
echo            考试系统 发行包完整性校验
echo ============================================================
echo   逐个比对 SHA256, 约 90MB, 视磁盘速度约需 10 秒到 1 分钟 ...
echo.

set /a N=0
set /a BAD=0
set "LIST=%TEMP%\exam_verify_%RANDOM%.txt"
del /q "%LIST%" >nul 2>&1

REM for /f 的默认分隔符是空格和制表符，会自动跳过连续空格；
REM 若用 tokens=1,2 仅取两个字段，哈希与文件名之间的两个空格会让 %%B 变成空值。
for /f "usebackq tokens=1,*" %%A in ("SHA256SUMS.txt") do (
  if not "%%A"=="" (
    set "FILE=%%B"
    set /a N+=1
    if not exist "%%B" (
      echo   缺失  %%B
      set /a BAD+=1
    ) else (
      set "ACT="
      for /f "skip=1 delims=" %%H in ('certutil -hashfile "%%B" SHA256 2^>nul') do (
        if not defined ACT set "ACT=%%H"
      )
      set "ACT=!ACT: =!"
      if /i not "!ACT!"=="%%A" (
        echo   损坏  %%B
        set /a BAD+=1
      )
    )
  )
)

echo.
echo ------------------------------------------------------------
if %BAD%==0 (
  echo 校验通过：%N% 个文件全部一致。
  echo 可以运行 bin\start.bat 启动系统。
  set "RC=0"
) else (
  echo 校验失败：%BAD% / %N% 个文件异常。
  echo 请重新解压或重新获取发行包，不要使用已损坏的包启动。
  set "RC=1"
)
echo ------------------------------------------------------------
pause
exit /b %RC%
