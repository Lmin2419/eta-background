#define _GNU_SOURCE
#include <link.h>
#include <stdint.h>
#include <string.h>
unsigned int la_version(unsigned int v) { return LAV_CURRENT; }
unsigned int la_objopen(struct link_map *m, Lmid_t l, uintptr_t *cookie) { *cookie=(uintptr_t)m; return LA_FLG_BINDTO|LA_FLG_BINDFROM; }
uintptr_t la_symbind64(Elf64_Sym *sym, unsigned int ndx, uintptr_t *ref, uintptr_t *def, unsigned int *flags, const char *name) {
struct link_map *r=(struct link_map*)*ref;
if (strstr(r->l_name, "librender_service_client.so")) {
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner11GetThreadIdEv")) return r->l_addr+0x3b6b50;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner18GetMainEventRunnerEv")) return r->l_addr+0x3b7310;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner21IsCurrentRunnerThreadEv")) return r->l_addr+0x3b72e0;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner3RunEv")) return r->l_addr+0x3b60a0;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner7CurrentEv")) return r->l_addr+0x3b5da0;
}
if (strstr(r->l_name, "librender_service_base.so")) {
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner11GetThreadIdEv")) return r->l_addr+0x84a010;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner18GetMainEventRunnerEv")) return r->l_addr+0x84a7d0;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner21IsCurrentRunnerThreadEv")) return r->l_addr+0x84a7a0;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner3RunEv")) return r->l_addr+0x849560;
if (!strcmp(name,"_ZN4OHOS10AppExecFwk11EventRunner7CurrentEv")) return r->l_addr+0x849260;
}
return sym->st_value; }
