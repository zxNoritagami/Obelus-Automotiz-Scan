@echo off
setlocal enabledelayedexpansion

:: ==========================================
:: CONFIGURACION DE ENTORNO JAVA (CRITICO)
:: ==========================================
set "JAVA_HOME=C:\Users\Angelo\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

:: Verificar Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No se detecto Java 17 en la ruta especificada.
    echo Ruta: "%JAVA_HOME%"
    pause
    exit /b
)

:: ==========================================
:: ACTUALIZADOR AUTOMATICO OBELUS
:: ==========================================
set REPO_PATH=C:\Users\Angelo\Desktop\scaner\scanner pro\repos\Obelus

echo ======================================================
echo           ACTUALIZADOR AUTOMATICO OBELUS
echo ======================================================

if not exist "%REPO_PATH%" (
    echo [ERROR] No se encuentra la carpeta del proyecto en:
    echo "%REPO_PATH%"
    pause
    exit /b
)

cd /d "%REPO_PATH%"

:: Generar Wrapper si no existe (Intento automatico)
if not exist "gradlew.bat" (
    echo [INFO] Generando Gradle Wrapper...
    call gradle wrapper --gradle-version 8.4 --distribution-type bin
)

echo [1/3] Preparando archivos...
git add .

set dt=%DATE% %TIME%
echo [2/3] Creando commit: "Auto-update: %dt%"
git commit -m "Auto-update: %dt%"

echo [3/3] Subiendo a GitHub (Rama main)...
git push origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ======================================================
    echo     EXITO: Repositorio actualizado correctamente
    echo ======================================================
) else (
    echo.
    echo [ERROR] Hubo un problema al subir los cambios.
    echo Verifica tu conexion o permisos.
)

:: Pausa breve para leer mensajes
timeout /t 5
exit /b
