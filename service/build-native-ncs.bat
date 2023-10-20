@ECHO OFF
REM Build a new ncs.exe 
ECHO Building new network configuration service executable (ncs.exe)

set GRALL_HOME=C:\Program Files\graalvm-ce-java17-22.0.0.2
set JAVA_HOME=%GRAAL_HOME%

mvn clean package -P native-network-configuration-service