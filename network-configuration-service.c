#include <windows.h>
#include <tchar.h>
#include <strsafe.h>
#include <stdio.h>

void srv_log_close(HANDLE hndl) {
	CloseHandle(hndl);
}

HANDLE srv_log_open() {
	HANDLE hndl;
	hndl = CreateFile("logs/z.log", FILE_APPEND_DATA,        // open for writing
			FILE_SHARE_READ,          // allow multiple readers
			NULL,                     // no security
			OPEN_ALWAYS,              // open or create
			FILE_ATTRIBUTE_NORMAL,    // normal file
			NULL);                    // no attr. template
	if (hndl != INVALID_HANDLE_VALUE) {
		if (GetLastError() != ERROR_ALREADY_EXISTS) {
			unsigned char Header[2];
			Header[0] = 0xFF;
			Header[1] = 0xFE;
			DWORD wr;
			WriteFile(hndl, Header, 2, &wr, NULL);
		}
		SetFilePointer(hndl, 0, 0, FILE_END);
	}
	return hndl;
}

void srv_log_write(HANDLE hndl, LPCWSTR txt) {
	DWORD bytesWritten = 0;
	WriteFile(hndl, txt, (int) wcslen(txt) * 2, &bytesWritten,
			NULL);
}

void srv_log_writenw(HANDLE hndl, char *txt) {
	DWORD bytesWritten = 0;
	WriteFile(hndl, txt, (int) strlen(txt), &bytesWritten,
			NULL);
}

void srv_log(LPCWSTR txt) {
	HANDLE logHndl = srv_log_open();
	if (logHndl != INVALID_HANDLE_VALUE) {
		// Not much we can do if there is an error I suppose. Perhaps this should just exit or something
		srv_log_write(logHndl, txt);
		srv_log_close(logHndl);
	}
}

void srv_lognw(TCHAR * txt) {
	HANDLE logHndl = srv_log_open();
	if (logHndl != INVALID_HANDLE_VALUE) {
		// Not much we can do if there is an error I suppose. Perhaps this should just exit or something
		srv_log_writenw(logHndl, txt);
		srv_log_close(logHndl);
	}
}

int __cdecl _tmain(int argc, TCHAR *argv[])
{
	if( argc != 4 || lstrcmpi( argv[1], TEXT("/service")) != 0 )
	{
		return 1;
	}

	SetCurrentDirectory(argv[2]);
	srv_log(L"Opening tunnel.dll\r\n");

	HMODULE tunnel_lib = LoadLibrary("tunnel.dll");
	if (!tunnel_lib)
	{
		srv_log(L"No dll\r\n");
		return 2;
	}

	srv_log(L"Looking up procedure\r\n");
	BOOL (_cdecl *tunnel_proc)(_In_ LPCWSTR conf_file);
	*(FARPROC*)&tunnel_proc = GetProcAddress(tunnel_lib, "WireGuardTunnelService");
	if (!tunnel_proc)
	{
		srv_log(L"No proc\r\n");
		return 3;
	}
	srv_log(L"Got proc\r\n");
	srv_lognw(argv[3]);

	//wchar_t wcstr[256];
	//vswprintf(wcstr, 256, L"conf\\connections\\%s.conf", argv[3]);

	auto wcstr = "conf\\connection\\" + argv[3];
	
	// Report running status when initialization is complete.

	// TO_DO: Perform work until service stops.
	// tunnel_proc(wcstr);
	srv_log(L"Running tunnel\r\n");
	srv_log(wcstr);
	tunnel_proc(wcstr);
	return 0;
}
