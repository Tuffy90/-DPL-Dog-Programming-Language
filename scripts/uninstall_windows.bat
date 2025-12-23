@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "EXT=.dog"
set "PROGID=DPL.DogFile"

echo.
echo === DPL Uninstall (Windows) ===

reg delete "HKCU\Software\Classes\%EXT%" /f >nul 2>nul
reg delete "HKCU\Software\Classes\%PROGID%" /f >nul 2>nul

echo ✅ Uninstalled.
echo If icons still show: restart Explorer or sign out/in.
echo.
pause
