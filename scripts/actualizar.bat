@echo off
setlocal enabledelayedexpansion

set "REPO_PATH=C:\Users\Angelo\Desktop\scaner\scanner pro\repos\Obelus"

echo ======================================================
echo         ACTUALIZADOR GITHUB - OBELUS
echo ======================================================

:: Verificar Git
git --version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Git no encontrado. Instala Git for Windows.
    pause & exit /b 1
)

:: Verificar repo
if not exist "%REPO_PATH%\.git" (
    echo [ERROR] Repositorio no encontrado en: %REPO_PATH%
    pause & exit /b 1
)

cd /d "%REPO_PATH%"

:: Obtener rama activa
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set "BRANCH=%%b"
echo [INFO] Rama activa: %BRANCH%

:: Stage
echo.
echo [1/3] Agregando cambios...
git add -A
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] git add fallo.
    pause & exit /b 1
)

:: Verificar si hay algo que commitear
git diff --cached --quiet
if %ERRORLEVEL% EQU 0 (
    echo [INFO] No hay cambios nuevos. Repositorio al dia.
    timeout /t 3 & exit /b 0
)

:: Fecha robusta (no depende del locale de Windows)
for /f "delims=" %%d in ('powershell -NoProfile -Command "Get-Date -Format \"yyyy-MM-dd HH:mm\""') do set "dt=%%d"

:: Commit
echo [2/3] Creando commit...
git commit -m "chore: actualizacion automatica %dt%"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] git commit fallo.
    pause & exit /b 1
)

:: Push
echo [3/3] Subiendo a GitHub (%BRANCH%)...
git push origin %BRANCH%
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================================
    echo   [OK] Subido correctamente a GitHub / rama: %BRANCH%
    echo ======================================================
) else (
    echo.
    echo [ERROR] git push fallo. Verifica credenciales o conexion.
    pause & exit /b 1
)

timeout /t 4
exit /b 0
