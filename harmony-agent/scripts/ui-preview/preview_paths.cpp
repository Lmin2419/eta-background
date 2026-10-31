// Linux Previewer compatibility: the previewer passes page paths relative to the module
// ("pages/Index"), while records in modules.abc carry the module prefix ("entry/ets/pages/Index").
#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <string>

#define EXECUTE_MODULE_BUFFER "_ZN5panda6JSNApi19ExecuteModuleBufferEPNS_10ecmascript6EcmaVMEPKhiRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEEb"
extern "C" bool preview_execute(void*, const unsigned char*, int, const std::string&, bool) asm(EXECUTE_MODULE_BUFFER);
extern "C" bool preview_execute(void* vm, const unsigned char* data, int size, const std::string& path, bool merged) {
    using Execute = bool (*)(void*, const unsigned char*, int, const std::string&, bool);
    static Execute original = reinterpret_cast<Execute>(dlsym(RTLD_NEXT, EXECUTE_MODULE_BUFFER));
    if (!original) return false;
    const char* module = std::getenv("PREVIEW_MODULE");
    std::string prefix = std::string(module && *module ? module : "entry") + "/ets/";
    if (!path.empty() && path[0] != '/' && path.rfind(prefix, 0) != 0) {
        std::string corrected = prefix + path;
        std::fprintf(stderr, "[preview-shim] entry %s -> %s\n", path.c_str(), corrected.c_str());
        return original(vm, data, size, corrected, merged);
    }
    return original(vm, data, size, path, merged);
}
