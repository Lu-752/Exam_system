@echo off
chcp 65001 >nul
rem 本机 JDK / Maven 未进系统 PATH，统一在此注入，避免污染全局环境
set "JAVA_HOME=C:\Users\lxq\.workbuddy\binaries\jdk\21"
set "MAVEN_HOME=C:\Users\lxq\.workbuddy\binaries\maven\3.9.16"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"
rem 宿主环境可能注入 SERVER_PORT，这里显式锁定 8080
set "SERVER_PORT=8080"

cd /d "%~dp0..\server"

if /i "%1"=="run" (
  echo [exam] starting backend on :8080 ...
  call mvn -q spring-boot:run
  exit /b
)
if /i "%1"=="build" (
  call mvn -B -DskipTests package
  exit /b
)
if /i "%1"=="test" (
  call mvn -B test
  exit /b
)

echo usage: scripts\backend.cmd [run^|build^|test]
