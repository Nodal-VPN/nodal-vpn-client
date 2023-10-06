@ECHO OFF
REM Clean up LogonBox VPN Services
ECHO Removing VPN Interfaces
FOR /L %%x IN (0,1,255) DO (
	ECHO|SET /p="."
	SC STOP LogonBoxVPNTunnel$net%%x > nul 2>&1
	SC DELETE LogonBoxVPNTunnel$net%%x > nul 2>&1
)