@echo off
chcp 65001 >nul

rem --- clean + build into out ---
rmdir /s /q out 2>nul
mkdir out

javac -encoding UTF-8 -d out *.java
if errorlevel 1 (
  echo.
  echo ❌ Compile failed.
  pause
  exit /b 1
)

echo.
echo ✅ Build OK. Starting...
java -cp out Code

echo.
pause