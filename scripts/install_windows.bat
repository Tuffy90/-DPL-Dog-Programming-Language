@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

for %%I in ("%~dp0..") do set "ROOT=%%~fI"

set "EXT=.dog"
set "PROGID=DPL.DogFile"
set "ICON=%ROOT%\assets\icons\dog.ico"
set "JAR=%ROOT%\dist\dpl.jar"

echo.
echo === DPL Install (Windows) ===
echo Root: "%ROOT%"
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo ❌ Java not found. Install Java 8+.
  echo.
  pause
  exit /b 1
)
if not exist "%JAR%" (
  echo ❌ Missing jar: "%JAR%"
  echo Build it first: scripts\build_jar_windows.bat
  echo.
  pause
  exit /b 1
)
if not exist "%ICON%" (
  echo ❌ Missing icon: "%ICON%"
  echo Put dog.ico into assets\icons\dog.ico
  echo.
  pause
  exit /b 1
)
set "OPEN_CMD=java -jar \"%JAR%\" \"%%1\""
echo ✅ Writing registry (HKCU\Software\Classes) ...

reg add "HKCU\Software\Classes\%EXT%" /ve /d "%PROGID%" /f >nul
reg add "HKCU\Software\Classes\%PROGID%" /ve /d "DPL Source File" /f >nul
reg add "HKCU\Software\Classes\%PROGID%\DefaultIcon" /ve /d "\"%ICON%\"" /f >nul
reg add "HKCU\Software\Classes\%PROGID%\shell\open\command" /ve /d "%OPEN_CMD%" /f >nul

echo ✅ Installed (.dog double-click will run via dpl.jar).
echo If icon doesn't refresh: restart Explorer or sign out/in.
echo.
pause