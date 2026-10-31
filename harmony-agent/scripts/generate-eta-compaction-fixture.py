#!/usr/bin/env python3
# Ported fixture from AgentCompactionProviderTest.kt @ 3b1794d.
# Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
"""Execute the original catalog's build(false, false) on a host JVM. No tool executes.

Requires JDK (or JRE + Eclipse ecj.jar) and Kotlin 2.0.21 compiler/runtime jars + org.json 20240303 in --jars.
Original catalog/schema/requirement bodies are unchanged. Android capture() and unused personal-search execution methods are
excluded, because the fixture explicitly uses the constructor defaults. Unused
false feature branches throw if called; they cannot silently shorten the fixture.
"""
import argparse, hashlib, json, pathlib, re, subprocess, tempfile, shutil
p=argparse.ArgumentParser();p.add_argument('--eta-root',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[2]);p.add_argument('--jars',type=pathlib.Path,required=True);p.add_argument('--shadow-source',type=pathlib.Path);args=p.parse_args()
root=args.eta_root;project=pathlib.Path(__file__).resolve().parents[1];jars=args.jars.resolve();manifest={}
base='app/src/main/kotlin/io/github/mangi/eta/agent/'
def read(path):
 data=(root/path).read_bytes();manifest[path]=hashlib.sha256(data).hexdigest();return data.decode()
with tempfile.TemporaryDirectory() as tmp:
 out=pathlib.Path(tmp);classes=out/'classes';classes.mkdir()
 names=['AgentToolCatalog','AgentToolSchema','AgentScreenObservationContract','AgentContextAppToolCatalog','AgentGestureToolCatalog','AgentTextSystemToolCatalog','AgentBackgroundToolCatalog','AgentDeviceToolCatalog','AgentPhoneToolCatalog','AgentPersonalSearchToolCatalog','AgentSkillToolCatalog']
 for name in names:(out/(name+'.kt')).write_text(read(base+'model/'+name+'.kt'))
 for name,area in [('AgentToolRequirements','tool'),('PersonalSearchTools','context')]:
  source=read(base+area+'/'+name+'.kt')
  if name=='PersonalSearchTools': source=source[:source.index('internal class IndexedPersonalSearch')]
  (out/(name+'.kt')).write_text(source)
 cap=read(base+'tool/AgentToolCapabilities.kt');cap=cap[:cap.index('    companion object {')]+'}\n';cap=re.sub(r'^import (?!org\.json)[^\n]+\n','',cap,flags=re.M);cap=cap.replace('RootAccessState?','Any?');(out/'Capabilities.kt').write_text(cap)
 state=read(base+'tool/SystemStateControls.kt');targets=re.search(r'val targets\s*=\s*(listOf\(.*?\))',state,re.S)[1];(out/'State.kt').write_text('package io.github.mangi.eta.agent.tool\ninternal object SystemStateControls { val targets = '+targets+' }')
 stub='package io.github.mangi.eta.agent.model\nimport org.json.JSONArray\n'
 for name in ['AgentWebToolCatalog','AgentBrowserToolCatalog','AgentFileVisionToolCatalog','AgentTerminalToolCatalog','AgentFileToolCatalog']:
  stub+='internal object '+name+' { val names: Set<String> get() = error("Excluded branch executed"); fun appendTo(tools:JSONArray, includeSearch:Boolean=true):Unit = error("Excluded branch executed") }\n'
 stub+='internal object AgentMemoryToolCatalog { fun appendTo(tools:JSONArray,writable:Boolean):Unit = error("Excluded branch executed") }\n'
 (out/'Excluded.kt').write_text(stub)
 (out/'Main.kt').write_text('package io.github.mangi.eta.agent.model\nfun main() { println(AgentToolCatalog.build(terminalTools=false,browserTools=false).toString()) }')
 path='app/src/main/java/com/eta/shadow/ShadowSchema.java'
 if args.shadow_source:
  data=args.shadow_source.read_bytes();manifest[path]=hashlib.sha256(data).hexdigest();shadow=data.decode()
 else:shadow=read(path)
 (out/'ShadowSchema.java').write_text(shadow)
 java_compiler=['javac'] if shutil.which('javac') else ['java','-jar',str(jars/'ecj.jar'),'-17']
 subprocess.run([*java_compiler,'-cp',str(jars/'json.jar'),'-d',str(classes),str(out/'ShadowSchema.java')],check=True)
 cp=':'.join([str(jars/'stdlib.jar'),str(jars/'annotations.jar'),str(jars/'json.jar'),str(classes)])
 subprocess.run(['java','-cp',str(jars/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp,'-d',str(classes),*map(str,out.glob('*.kt'))],check=True)
 data=json.loads(subprocess.check_output(['java','-cp',cp,'io.github.mangi.eta.agent.model.MainKt']))
 target=project/'tests/eta/support/compactionToolsFixture.json';target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
 manifest={'sourceCommit':'3b1794d70881c3c849b53d7778dd2bfdf0a46cc5','requiredNotice':'Copyright © 2026 蛮吉 (Mangi-11).','sources':manifest,'toolCount':len(data),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()}
 (target.parent/'compactionToolsFixture.provenance.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print('Generated complete source fixture:',len(data),'tools')
