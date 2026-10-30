"""Native Previewer control/capture bridge. No app-state or pixel fabrication."""
import asyncio, json, os, pathlib, shutil, signal, socket, subprocess, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs
from websockets.asyncio.client import connect

root=pathlib.Path('/workspace/harmony-agent')
sdk=pathlib.Path('/workspace/toolchain/command-line-tools/sdk/default/openharmony')
out=root/'dist/screenshots-native'
out.mkdir(exist_ok=True)
width=int(os.environ.get('PREVIEW_WIDTH','1264')); height=int(os.environ.get('PREVIEW_HEIGHT','1848'))
density=os.environ.get('PREVIEW_DENSITY','320')
name='foundation-capture'
clients={}; state={'tree':{},'frames':0,'latest':None,'responses':[]}; stopped=threading.Event()

def listen(suffix):
    path='/tmp/'+name+suffix
    try:os.unlink(path)
    except FileNotFoundError:pass
    s=socket.socket(socket.AF_UNIX);s.bind(path);s.listen(1)
    def read():
        c,_=s.accept();clients[suffix]=c;buffer=b''
        with (out/('live-ipc'+suffix+'.log')).open('wb') as log:
            while not stopped.is_set():
                try:data=c.recv(65536)
                except OSError:break
                if not data:break
                log.write(data);log.flush();buffer+=data
                while b'\0' in buffer:
                    part,buffer=buffer.split(b'\0',1)
                    try:
                        item=json.loads(part);state['responses'].append(item)
                        if item.get('command')=='inspector':state['tree']=json.loads(item['result'])
                    except (ValueError,KeyError):pass
    threading.Thread(target=read,daemon=True).start()
    return s
servers=[listen('_commandPipe'),listen('_imagePipe'),listen('-trace')]
def command(name,args={},kind='action'):
    c=clients.get('_commandPipe')
    if not c:raise RuntimeError('Command socket not connected')
    c.sendall(json.dumps({'version':'1.0.1','type':kind,'command':name,'args':args},ensure_ascii=False).encode()+b'\0')

def nodes():
    found=[]
    def walk(n):
        if isinstance(n,dict):
            attrs=n.get('$attrs',{})
            if n.get('$type') in ['Text','Button','TextInput','TextArea','Image','Scroll','Toggle']:
                found.append({'type':n.get('$type'),'id':n.get('$ID'), 'rect':n.get('$rect'),'text':attrs.get('content',attrs.get('text',attrs.get('label',''))), 'placeholder':attrs.get('placeholder',''), 'resource':attrs.get('src',''), 'attrs':{k:attrs[k] for k in ['id','enabled','isOn'] if k in attrs}})
            for v in n.values():
                if isinstance(v,(dict,list)):walk(v)
        elif isinstance(n,list):
            for v in n:walk(v)
    walk(state['tree']);return found

class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        url=urlparse(self.path)
        if url.path=='/image':
            data=state['latest'] or b''; self.send_response(200);self.send_header('Content-Type','image/jpeg');self.end_headers();self.wfile.write(data);return
        if url.path=='/state':
            command('inspector');time.sleep(.15);payload={'frames':state['frames'],'nodes':nodes()}
        else:payload={'error':'Use /state or /image'}
        self.reply(payload)
    def reply(self,payload):
        self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(payload,ensure_ascii=False).encode())
    def do_POST(self):
        try:
            body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
            if self.path=='/tap':
                command('MousePress',{'x':int(body['x']),'y':int(body['y'])});time.sleep(.06);command('MouseRelease',{'x':int(body['x']),'y':int(body['y'])})
            elif self.path=='/text':
                for char in body['text']:command('KeyPress',{'isInputMethod':True,'codePoint':ord(char)});time.sleep(.012)
            elif self.path=='/command':command(body['command'],body.get('args',{}),body.get('type','action'))
            elif self.path=='/capture':
                name=body['name'];assert name.replace('-','').replace('_','').isalnum()
                assert state['latest'] is not None
                (out/(name+'.jpg')).write_bytes(state['latest']);command('inspector');time.sleep(.15)
                (out/(name+'-tree.json')).write_text(json.dumps(state['tree'],ensure_ascii=False,indent=2))
            elif self.path=='/quit':stopped.set()
            self.reply({'ok':True,'frames':state['frames']})
        except Exception as e:self.reply({'ok':False,'error':str(e)})

async def run():
    pbase=root/'entry/.preview/default/intermediates'
    cmd=[str(sdk/'previewer/common/bin/Previewer'),'-j','/data/storage/el1/bundle/entry/ets/modules.abc','-n',name,'-s',name,'-ts',name+'-trace','-or',str(width),str(height),'-cr',str(width),str(height),'-device','phone','-shape','rect','-url','pages/FoundationPreview','-pm','Stage','-av','ACE_2_0','-sd',density,'-l','zh_CN','-cm','light','-o','portrait' if height>width else 'landscape','-sm','dynamic','-lws','44797','-arp',str(pbase/'res/default'),'-ljPath',str(pbase/'loader/default/loader.json'),'-bn','com.example.harmonyagent']
    cmd=['bwrap','--tmpfs','/','--ro-bind','/usr','/usr','--ro-bind','/lib','/lib','--ro-bind','/lib64','/lib64','--ro-bind','/etc','/etc','--ro-bind','/opt','/opt','--bind','/workspace','/workspace','--bind','/tmp','/tmp','--dev-bind','/dev','/dev','--proc','/proc','--bind',str(pbase/'loader_out/default/ets'),'/data/storage/el1/bundle/entry/ets/modules.abc']+cmd
    env=dict(os.environ,LD_LIBRARY_PATH='/workspace/preview-runtime/lib',DISPLAY=':97',LIBGL_ALWAYS_SOFTWARE='1',LD_AUDIT='/workspace/preview-runtime/audit.so',LD_PRELOAD='/workspace/preview-runtime/preview_paths.so')
    log=(out/'interactive-native.log').open('wb')
    proc=subprocess.Popen(cmd,cwd=sdk/'previewer/common/bin',env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,start_new_session=True)
    def readlog():
        size=0
        for line in proc.stdout:
            if size<12_000_000:log.write(line);log.flush();size+=len(line)
            if b'[JsEngine Crash]Engine Crash Info End.' in line:
                stopped.set();break
    threading.Thread(target=readlog,daemon=True).start()
    http=ThreadingHTTPServer(('127.0.0.1',8877),Handler);threading.Thread(target=http.serve_forever,daemon=True).start()
    try:
        for _ in range(100):
            try:ws=await connect('ws://127.0.0.1:44797',max_size=20_000_000,proxy=None);break
            except OSError:
                if proc.poll() is not None:raise RuntimeError('Previewer exited')
                await asyncio.sleep(.1)
        else:raise RuntimeError('No image socket')
        async with ws:
            while not stopped.is_set() and proc.poll() is None:
                try:data=await asyncio.wait_for(ws.recv(),.5)
                except asyncio.TimeoutError:continue
                if isinstance(data,bytes):
                    pos=data.find(b'\xff\xd8\xff')
                    if pos>=0:
                        state['latest']=data[pos:];state['frames']+=1
                        (out/'latest.jpg').write_bytes(data[pos:])
                        if state['frames']==1:print('Native preview is ready on http://127.0.0.1:8877',flush=True)
    finally:
        stopped.set();http.shutdown()
        if proc.poll() is None:os.killpg(proc.pid,signal.SIGKILL)
        proc.wait();log.close()
asyncio.run(run())
