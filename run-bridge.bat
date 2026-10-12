@echo off
setlocal
title Baton Desktop Bridge
echo Starting Baton Desktop Bridge...
echo.

node -v >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js is required to run Baton Bridge.
    echo Please install Node.js from https://nodejs.org
    echo.
    pause
    exit /b 1
)

node "%~dp0tools\baton-bridge\bin\baton-bridge.js" %*
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Bridge encountered an error.
    pause
)
