@REM
@REM Copyright ©2023-2025 LogonBox Ltd
@REM All changes post March 2025 Copyright © ${project.inceptionYear} JADAPTIVE Limited (support@jadaptive.com)
@REM
@REM This program is free software: you can redistribute it and/or modify
@REM it under the terms of the GNU General Public License as published by
@REM the Free Software Foundation, either version 3 of the License, or
@REM (at your option) any later version.
@REM
@REM This program is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
@REM GNU General Public License for more details.
@REM
@REM You should have received a copy of the GNU General Public License
@REM along with this program.  If not, see <https://www.gnu.org/licenses/>.
@REM

@ECHO OFF
REM Clean up LogonBox VPN Services
ECHO Removing VPN Interfaces
FOR /L %%x IN (1,1,255) DO (
	ECHO|SET /p="."
	SC STOP NodalVPNTunnel$net%%x > nul 2>&1
	SC DELETE NodalVPNTunnel$net%%x > nul 2>&1
)