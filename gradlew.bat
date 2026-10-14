@echo off
python "%~dp0scripts\gradle_bootstrap.py" %*
exit /b %ERRORLEVEL%
