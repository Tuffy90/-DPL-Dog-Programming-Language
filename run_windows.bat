@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

rem =========================
rem DPL - Run (Windows)
rem Requires: Java 8+ (JRE/JDK)
rem Usage:
rem   run_windows.bat
rem   run_windows.bat path\file.dog
rem   run_windows.bat path\file.dogc
rem =========================

rem Этот bat лежит в корне проекта. Берём ROOT от его расположения,
rem чтобы запуск работал из любой текущей папки.
for %%I in ("%~dp0.") do set "ROOT=%%~fI"
set "DIST_DIR=%ROOT%\dist"
set "JAR_NAME=dpl.jar"

echo.
echo === DPL Run (Windows) ===
echo Root: "%ROOT%"
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo ❌ Java not found.
  echo Install Java 8+.
  echo.
  pause
  exit /b 1
)

if not exist "%DIST_DIR%\%JAR_NAME%" (
  echo ❌ "%DIST_DIR%\%JAR_NAME%" not found.
  echo Run scripts\build_jar_windows.bat first.
  echo.
  pause
  exit /b 1
)

rem Ensure folders exist for users
if not exist "%ROOT%\out\" mkdir "%ROOT%\out" >nul 2>nul
if not exist "%ROOT%\test_files_RDL\" mkdir "%ROOT%\test_files_RDL" >nul 2>nul

echo ✅ Starting DPL...
rem Передаём ВСЕ аргументы дальше (путь к .dog/.dogc и т.д.)
java -jar "%DIST_DIR%\%JAR_NAME%" %*

echo.
pause