#include <dlfcn.h>
#include <string>
#include <cstdio>

// Linux SDK compatibility only: Previewer supplies a relative page URL, while
// this SDK's Ark runtime expects the bundled module prefix in its entry point.
extern "C" bool preview_execute(void*, const unsigned char*, int, const std::string&, bool)
    asm("_ZN5panda6JSNApi19ExecuteModuleBufferEPNS_10ecmascript6EcmaVMEPKhiRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEEb");
extern "C" bool preview_execute(void* vm, const unsigned char* data, int size, const std::string& path, bool merged) {
    using Execute = bool(*)(void*, const unsigned char*, int, const std::string&, bool);
    auto original = reinterpret_cast<Execute>(dlsym(RTLD_NEXT,
        "_ZN5panda6JSNApi19ExecuteModuleBufferEPNS_10ecmascript6EcmaVMEPKhiRKNSt7__cxx1112basic_stringIcSt11char_traitsIcESaIcEEEb"));
    if (!original) return false;
    if (path.rfind("pages/", 0) == 0) {
        auto corrected = std::string("entry/ets/") + path;
        std::fprintf(stderr, "[Preview compatibility] Entry URL: %s -> %s\n", path.c_str(), corrected.c_str());
        return original(vm, data, size, corrected, merged);
    }
    return original(vm, data, size, path, merged);
}
