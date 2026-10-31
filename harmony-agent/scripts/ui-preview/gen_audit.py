"""Linux Previewer compatibility (see setup.sh). Generate an LD_AUDIT library that makes selected previewer libraries bind their own
AppExecFwk symbols (like -Bsymbolic). Offsets are read from the SDK's ELF files, not hard-coded."""
import subprocess, sys, pathlib
bin_dir = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2])
NAMESPACE = 'AppExecFwk'
owners = {}
for so in sorted(bin_dir.glob('*.so')):
    syms = subprocess.run(['readelf', '-Ws', '--dyn-syms', str(so)], capture_output=True, text=True).stdout
    for line in syms.splitlines():
        parts = line.split()
        if len(parts) >= 8 and parts[6] != 'UND' and parts[3] == 'FUNC' and NAMESPACE in parts[7]:
            owners.setdefault(so.name, {})[parts[7].split('@')[0]] = int(parts[1], 16)
# Only libraries whose AppExecFwk functions are also defined elsewhere need isolation.
names = {}
for lib, table in owners.items():
    for sym in table: names.setdefault(sym, set()).add(lib)
dups = {lib: {s: off for s, off in table.items() if len(names[s]) > 1} for lib, table in owners.items()}
dups = {lib: t for lib, t in dups.items() if t}
lines = ['#define _GNU_SOURCE', '#include <link.h>', '#include <stdint.h>', '#include <string.h>',
         'struct entry { const char *lib; const char *sym; uintptr_t off; };', 'static const struct entry table[] = {']
for lib, table in sorted(dups.items()):
    for sym, off in sorted(table.items()):
        lines.append('  {"/%s", "%s", 0x%x},' % (lib, sym, off))
lines += ['  {0, 0, 0}', '};',
 'unsigned int la_version(unsigned int v) { return LAV_CURRENT; }',
 'unsigned int la_objopen(struct link_map *m, Lmid_t l, uintptr_t *cookie) { *cookie = (uintptr_t)m; return LA_FLG_BINDTO | LA_FLG_BINDFROM; }',
 'uintptr_t la_symbind64(Elf64_Sym *sym, unsigned int ndx, uintptr_t *ref, uintptr_t *def, unsigned int *flags, const char *name) {',
 '  struct link_map *r = (struct link_map *)*ref;',
 '  if (!r->l_name || !strstr(name, "AppExecFwk")) return sym->st_value;',
 '  for (const struct entry *e = table; e->lib; e++) {',
 '    size_t n = strlen(r->l_name), k = strlen(e->lib);',
 '    if (n >= k && !strcmp(r->l_name + n - k, e->lib) && !strcmp(name, e->sym)) return r->l_addr + e->off;',
 '  }',
 '  return sym->st_value;', '}']
out.write_text('\n'.join(lines) + '\n')
print({lib: len(t) for lib, t in dups.items()})
