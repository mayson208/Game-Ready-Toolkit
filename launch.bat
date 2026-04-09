@echo off
:: Game Ready Toolkit — Launch Script
:: Requires Java 21+ installed

:: Check for admin — relaunch elevated if needed
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Requesting administrator privileges...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

:: Launch the toolkit
java -jar "%~dp0GameReadyToolkit.jar"
