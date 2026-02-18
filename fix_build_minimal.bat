@echo off
set "JAVA_HOME=C:\Users\Angelo\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\Windows\System32;C:\Windows"

echo [INFO] Running with minimal PATH...
echo JAVA_HOME: %JAVA_HOME%
where java
java -version

echo.
echo [INFO] Generating Gradle Wrapper...
call gradle wrapper --gradle-version 8.4 --distribution-type bin --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Wrapper generation failed.
    exit /b 1
)

echo [INFO] Wrapper generated.
echo [INFO] Compiling (Debug)...
call gradlew assembleDebug --no-daemon
