@echo off
set "JAVA_HOME=C:\Users\Angelo\AppData\Local\Programs\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
set "PATH=%JAVA_HOME%\bin;C:\Gradle\gradle-9.3.0\bin;C:\Windows\System32;C:\Windows"

echo [INFO] Environment Setup:
echo JAVA_HOME: %JAVA_HOME%
where java
java -version
where gradle

echo.
echo [INFO] Generating Gradle Wrapper (v8.4)...
call gradle wrapper --gradle-version 8.4 --distribution-type bin --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Wrapper generation failed.
    exit /b 1
)

echo.
echo [INFO] Wrapper generated successfully.
echo [INFO] Compiling Release (assembleRelease)...
call gradlew assembleRelease --no-daemon
