@echo off
setlocal enabledelayedexpansion

:: ==========================================
:: CONFIGURACION DE ENTORNO
:: ==========================================
set "JAVA_HOME=C:\Users\Angelo\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "GRADLE_HOME=C:\Gradle\gradle-9.3.0"
set "PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%PATH%"
set "REPO_PATH=C:\Users\Angelo\Desktop\scaner\scanner pro\repos\Obelus"

echo ======================================================
echo         ACTUALIZADOR AUTOMATICO OBELUS v2
echo ======================================================

:: Verificar Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java 17 no encontrado en: %JAVA_HOME%
    pause & exit /b 1
)

:: Verificar repo
if not exist "%REPO_PATH%\.git" (
    echo [ERROR] Repositorio no encontrado en: %REPO_PATH%
    pause & exit /b 1
)

cd /d "%REPO_PATH%"

:: ── Generar wrapper si falta ──────────────────────────────────────────────
if not exist "gradlew.bat" (
    echo [INFO] Generando Gradle Wrapper 8.5...
    call gradle wrapper --gradle-version 8.5 --distribution-type bin --no-daemon
    if %ERRORLEVEL% NEQ 0 (
        echo [WARN] No se pudo generar wrapper, continuando solo con git push...
    )
)

:: ── Git: stage → commit → push ────────────────────────────────────────────
echo.
echo [1/3] Preparando archivos para commit...
git add -A
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] git add fallo.
    pause & exit /b 1
)

:: Verificar si hay cambios para commitear
git diff --cached --quiet
if %ERRORLEVEL% EQU 0 (
    echo [INFO] No hay cambios nuevos. El repositorio esta al dia.
    timeout /t 3 & exit /b 0
)

set "dt=%DATE:~6,4%-%DATE:~3,2%-%DATE:~0,2% %TIME:~0,8%"
set "MSG=fix: DBC Editor crash fix + actualizacion %dt%"
echo [2/3] Creando commit: "%MSG%"
git commit -m "%MSG%"
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] git commit fallo.
    pause & exit /b 1
)

echo [3/3] Subiendo a GitHub (main)...
git push origin main
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================================
    echo   [OK] Repositorio actualizado. GitHub Actions activo.
    echo ======================================================
) else (
    echo.
    echo [ERROR] git push fallo. Verifica credenciales o conexion.
    pause & exit /b 1
)

timeout /t 5
exit /b 0
