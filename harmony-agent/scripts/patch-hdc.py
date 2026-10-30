#!/usr/bin/env python3
"""Apply sandbox changes to the pinned upstream HDC source, without changing process HOME."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
src = root / 'vendor/developtools_hdc-OpenHarmony-6.0-Release'
if not (src / '.agent-patched').exists():
    # The USB-removal patch is Apache-2.0, credited in THIRD_PARTY_NOTICES.md.
    if 'HDC_NO_USB' not in (src/'src/host/host_usb.h').read_text():
        subprocess.run(['patch','-p1','-i',str(root/'scripts/no-usb.patch')],cwd=src,check=True)
    def replace(file, old, new):
        p=src/file; data=p.read_text()
        if old not in data: raise RuntimeError(f'Upstream changed: {file}')
        p.write_text(data.replace(old,new,1))
    replace('src/common/base.h','    string GetTmpDir();','    string GetTmpDir();\n    void SetAgentDir(const string &dir);')
    replace('src/common/base.cpp','    string GetTmpDir()','    void SetAgentDir(const string &dir) { g_tempDir = dir + "/"; }\n\n    string GetTmpDir()')
    replace('src/common/define.h','const string UDS_PATH = "/data/hdc/hdc_debug/hdc_server";', 'inline string UDS_PATH = ""; // Set once by the application before starting HDC.')
    replace('src/common/auth.cpp','    if (uv_os_homedir(buf, &len) < 0)\n        return false;','    if (strcpy_s(buf, len, Base::GetTmpDir().c_str()) != 0)\n        return false;')
    # Device automation must not persist command lines or shell output as host logs.
    replace('src/host/server.cpp','    Base::ProcessCmdLogs();','    // Embedded agent: command logging disabled.')
    (src/'.agent-patched').write_text('sandbox-v1\n')

# Upstream puts the timer constants behind UART, but the USB branch also uses them.
p = src / 'src/host/server.cpp'
data = p.read_text()
data = data.replace('uv_timer_start(waitTimeDoCmd, UsbPreConnect, UV_TIMEOUT, UV_REPEAT);',
                    'uv_timer_start(waitTimeDoCmd, UsbPreConnect, 10, 100);')
p.write_text(data)

p = src / 'src/common/base.cpp'
data = p.read_text()
data = data.replace('void SetAgentDir(const string &dir) { g_tempDir = dir + "/"; }',
    'void SetAgentDir(const string &dir) { g_tempDir = dir + "/"; g_encryptTCPSwitch = true; }')
data = data.replace('if (uv_os_homedir(buf, &size) < 0) {',
                    'if (strcpy_s(buf, size, GetTmpDir().c_str()) != 0) {')
data = data.replace('        umask(0);\n        uv_fs_t req;',
                    '        // Embedded: do not change the application process umask.\n        uv_fs_t req;')
data = data.replace('bufPath, O_RDWR | O_CREAT, 0666, nullptr)',
                    'bufPath, O_RDWR | O_CREAT, 0600, nullptr)')
p.write_text(data)
