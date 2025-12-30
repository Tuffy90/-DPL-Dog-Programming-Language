@echo off
setlocal EnableExtensions
chcp 65001 >nul

rem =========================
rem DPL - Uninstall (Windows)
rem Removes .dog association created by install script (per-user)
rem =========================

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