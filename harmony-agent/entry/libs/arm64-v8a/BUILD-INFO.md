# libagent_hdc.so

Built by `scripts/build-native-ohos.sh`. Do not edit by hand; rebuild with the script.

| Item | Value |
| --- | --- |
| SHA-256 | `6cf0b55f502e1d4257feeebcd887dad96327b339fae4bb94ce944988ec2a8a60` |
| Architecture | arm64-v8a (aarch64-linux-ohos) |
| Toolchain | OpenHarmony SDK native 26.0.0.38, OHOS (dev) clang version 15.0.4 (llvm-project 3f002d04ba6b3a7659c601207c89d3a896b82546) |
| C++ runtime | static (`OHOS_STL=c++_static`) |
| Runtime dependencies | libace_napi.z.so libc.so libhilog_ndk.z.so libuv.so |
| Sources | `scripts/native-sources.json` (HDC OpenHarmony-6.0 `10e867e`, OpenSSL 3.0.16, lz4 1.10.0, securec `61baa7a`), patched by `scripts/patch-hdc.py` and `scripts/no-usb.patch` |
| Exports | `start(privateDirectory)`, `command(command, target, timeoutMs)`, `cancel()`; typings in `entry/src/main/cpp/types/libagent_hdc` |

Licenses: HDC Apache-2.0; securec MulanPSL-2.0; OpenSSL Apache-2.0; lz4 BSD-2-Clause. See THIRD_PARTY_NOTICES.md.
