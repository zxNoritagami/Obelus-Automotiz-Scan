@echo off
setlocal enabledelayedexpansion

echo ============================================
echo      CONSTRUCTOR DE APK RELEASE (LOCAL)
echo ============================================

:: 1. CONFIGURACION DE RUTAS (JAVA & GRADLE)
set "JAVA_HOME=C:\Users\Angelo\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set GRADLE_HOME=C:\Gradle\gradle-8.6
set "PATH=%JAVA_HOME%\bin;%GRADLE_HOME%\bin;%PATH%"

echo [INFO] Entorno configurado:
echo JAVA_HOME: %JAVA_HOME%
echo GRADLE_HOME: %GRADLE_HOME%
echo.

:: Verificacion
java -version
call gradle -version
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] No se pudo ejecutar Gradle. Verifica las rutas.
    pause
    exit /b
)

:: 2. CONFIGURACION DE FIRMA
set "KEYSTORE_PASSWORD=1214242933_Angelo"
set "KEY_ALIAS=21566970k_Angelo"
set "KEY_PASSWORD=1214242933_Angelo"

:: 3. COPIAR KEYSTORE
if exist "C:\Users\Angelo\Desktop\release.keystore" (
    echo [INFO] Copiando release.keystore...
    copy /Y "C:\Users\Angelo\Desktop\release.keystore" "app\keystore.jks" >nul
) else (
    echo [ERROR] NO SE ENCONTRO 'release.keystore' EN EL ESCRITORIO.
    pause
    exit /b
)

:: 4. EJECUTAR BUILD (System Gradle)
echo.
echo [INFO] Iniciando compilacion Release (Gradle System)...
echo Esto tomara unos minutos...
echo.

call gradle assembleRelease --no-daemon --stacktrace

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo        [EXITO] APK GENERADO CORRECTAMENTE
    echo ============================================
    echo.
    echo UBICACION: app\build\outputs\apk\release\app-release.apk
    echo.
    del "app\keystore.jks"
    echo Puedes cerrar esta ventana.
) else (
    echo.
    echo ============================================
    echo        [FALLO] ERROR EN LA COMPILACION
    echo ============================================
    echo.
    if exist "app\keystore.jks" del "app\keystore.jks"
)

pause

