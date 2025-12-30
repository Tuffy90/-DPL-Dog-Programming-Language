@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT=%%~fI"

set "SRC_DIR=%ROOT%\src"
set "OUT_DIR=%ROOT%\out"
set "DIST_DIR=%ROOT%\dist"
set "JAR_NAME=dpl.jar"
set "MAIN_CLASS=Code"
set "LOG=%ROOT%\build_jar.log"

> "%LOG%" echo === DPL build log ===
>>"%LOG%" echo Root: "%ROOT%"
>>"%LOG%" echo.

echo.
echo === DPL Build JAR (Windows) ===
echo Root: "%ROOT%"
echo.

where javac >nul 2>nul
if errorlevel 1 goto :NO_JAVAC

where jar >nul 2>nul
if errorlevel 1 goto :NO_JAR

where java >nul 2>nul
if errorlevel 1 goto :NO_JAVA

if not exist "%SRC_DIR%\" goto :NO_SRC

if exist "%OUT_DIR%\" rmdir /s /q "%OUT_DIR%"
mkdir "%OUT_DIR%" >nul 2>nul
if not exist "%DIST_DIR%\" mkdir "%DIST_DIR%" >nul 2>nul
if not exist "%ROOT%\test_files_RDL\" mkdir "%ROOT%\test_files_RDL" >nul 2>nul
if not exist "%ROOT%\assets\icons\" mkdir "%ROOT%\assets\icons" >nul 2>nul

echo ✅ Compiling Java sources...
>>"%LOG%" echo [javac] compiling...
javac -encoding UTF-8 -d "%OUT_DIR%" "%SRC_DIR%\*.java" >>"%LOG%" 2>&1
if errorlevel 1 goto :COMPILE_FAIL

if not exist "%OUT_DIR%\%MAIN_CLASS%.class" goto :MAIN_NOT_FOUND

set "MF=%OUT_DIR%\manifest.mf"
> "%MF%" echo Manifest-Version: 1.0
>>"%MF%" echo Main-Class: %MAIN_CLASS%
>>"%MF%" echo.

echo ✅ Building JAR...
>>"%LOG%" echo [jar] building jar...
jar cfm "%DIST_DIR%\%JAR_NAME%" "%MF%" -C "%OUT_DIR%" . >>"%LOG%" 2>&1
if errorlevel 1 goto :JAR_FAIL

echo.
echo ✅ Done: "%DIST_DIR%\%JAR_NAME%"
echo Log: "%LOG%"
echo.
pause
exit /b 0

:NO_JAVAC
echo ❌ javac not found. You likely have only JRE, not JDK.
echo Install JDK 8+ (recommended 17).
echo.
pause
exit /b 1

:NO_JAR
echo ❌ jar tool not found (JDK required).
echo Install JDK 8+.
echo.
pause
exit /b 1

:NO_JAVA
echo ❌ java not found.
echo Install Java 8+.
echo.
pause
exit /b 1

:NO_SRC
echo ❌ src folder not found: "%SRC_DIR%"
echo Put Java sources into DPL\src\
echo.
pause
exit /b 1

:COMPILE_FAIL
echo.
echo ❌ Compile failed. See build_jar.log
echo.
type "%LOG%"
echo.
pause
exit /b 1

:MAIN_NOT_FOUND
echo.
echo ❌ Main class "%MAIN_CLASS%" not found in out\.
echo If Code.java uses a package, set MAIN_CLASS to full name (e.g. com.example.Code)
echo.
pause
exit /b 1

:JAR_FAIL
echo.
echo ❌ JAR build failed. See build_jar.log
echo.
type "%LOG%"
echo.
pause
exit /b 1
