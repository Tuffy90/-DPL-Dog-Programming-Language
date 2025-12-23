@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT=%%~fI"
set "SRC_DIR=%ROOT%\src"
set "OUT_DIR=%ROOT%\out"
set "DIST_DIR=%ROOT%\dist"
set "JAR_NAME=dpl.jar"
set "MAIN_CLASS=Code"

echo.
echo === DPL Build JAR (Windows) ===
echo Root: "%ROOT%"
echo.

where javac >nul 2>nul
if errorlevel 1 (
  echo ❌ javac not found. You have only JRE, not JDK.
  echo Install JDK 8+ (recommended 17).
  echo.
  pause
  exit /b 1
)

where jar >nul 2>nul
if errorlevel 1 (
  echo ❌ jar tool not found (JDK required).
  echo Install JDK 8+.
  echo.
  pause
  exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
  echo ❌ java not found.
  echo Install Java 8+.
  echo.
  pause
  exit /b 1
)

if not exist "%SRC_DIR%\" (
  echo ❌ src folder not found: "%SRC_DIR%"
  echo Put Java sources into DPL\src\
  echo.
  pause
  exit /b 1
)

if exist "%OUT_DIR%\" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%" >nul 2>nul
if not exist "%DIST_DIR%\" mkdir "%DIST_DIR%" >nul 2>nul
if not exist "%ROOT%\test_files_RDL\" mkdir "%ROOT%\test_files_RDL" >nul 2>nul
if not exist "%ROOT%\assets\icons\" mkdir "%ROOT%\assets\icons" >nul 2>nul

echo ✅ Compiling Java sources...
javac -encoding UTF-8 -d "%OUT_DIR%" "%SRC_DIR%\*.java"
if errorlevel 1 (
  echo.
  echo ❌ Compile failed.
  pause
  exit /b 1
)

set "MF=%OUT_DIR%\manifest.mf"
(
  echo Manifest-Version: 1.0
  echo Main-Class: %MAIN_CLASS%
  echo.
) > "%MF%"

echo ✅ Building JAR...
jar cfm "%DIST_DIR%\%JAR_NAME%" "%MF%" -C "%OUT_DIR%" .
if errorlevel 1 (
  echo.
  echo ❌ JAR build failed.
  pause
  exit /b 1
)

echo.
echo ✅ Done: "%DIST_DIR%\%JAR_NAME%"
echo.

pause
