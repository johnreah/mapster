@echo off
echo Building Mapster Windows Executable...

rem Clean and build
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    exit /b 1
)

rem Remove old distribution
if exist "target\dist" rd /s /q target\dist

rem Run jpackage to create Windows executable
jpackage ^
  --input target ^
  --name Mapster ^
  --main-jar mapster-1.0-SNAPSHOT.jar ^
  --main-class com.johnreah.mapster.App ^
  --module-path "target/jmods" ^
  --add-modules javafx.controls,javafx.graphics,java.net.http ^
  --type app-image ^
  --dest target/dist ^
  --app-version 1.0.0 ^
  --vendor johnreah ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --java-options "--enable-native-access=javafx.graphics"

rem Make executable writable
attrib -R "target\dist\Mapster\Mapster.exe"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS! Executable created successfully
    echo ========================================
    echo.
    echo Location: target\dist\Mapster\
    echo Run with: target\dist\Mapster\Mapster.exe
    echo.
    echo The entire Mapster folder can be copied to any Windows machine
    echo No JRE installation required - runtime is bundled
) else (
    echo.
    echo Build failed!
    exit /b 1
)
