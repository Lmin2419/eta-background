"""Drive the OpenHarmony Linux Previewer with scenario files: tap, type, wait, assert, screenshot.
usage: python3 scripts/ui-preview/uitest.py <build out dir> <scenario.json> <report dir>  (normally via run.sh)
Each step also fails on new ArkTS application errors, JS exceptions or a previewer crash.
"""
import asyncio, json, os, pathlib, re, signal, socket, subprocess, sys, threading, time
from websockets.asyncio.client import connect

ERROR_PATTERNS = [b'FIX THIS APPLICATION ERROR', b'TypeError:', b'ReferenceError:', b'RangeError:', b'SyntaxError:',
                  b'[JsEngine Crash]', b'Uncaught exception', b'Error message:']

class Preview:
    def __init__(self, build, page, report, width=1264, height=1848, density=320, color='light'):
        self.build, self.page, self.report = pathlib.Path(build), page, pathlib.Path(report)
        self.width, self.height, self.density, self.color = width, height, density, color
        self.name, self.port = 'eta-uitest-%d' % os.getpid(), 44700 + os.getpid() % 200
        self.clients, self.tree, self.frame, self.frames = {}, None, None, 0
        self.errors, self.stopped, self.crashed = [], threading.Event(), False
        self.tree_event = threading.Event()

    # Unix sockets the previewer connects to for commands and responses.
    def listen(self, suffix):
        path = '/tmp/' + self.name + suffix
        try: os.unlink(path)
        except FileNotFoundError: pass
        s = socket.socket(socket.AF_UNIX); s.bind(path); s.listen(1)
        def read():
            c, _ = s.accept(); self.clients[suffix] = c; buf = b''
            while not self.stopped.is_set():
                try: data = c.recv(1 << 16)
                except OSError: break
                if not data: break
                buf += data
                while b'\0' in buf:
                    part, buf = buf.split(b'\0', 1)
                    try:
                        item = json.loads(part)
                        if item.get('command') == 'inspector':
                            self.tree = json.loads(item['result']); self.tree_event.set()
                    except (ValueError, KeyError): pass
        threading.Thread(target=read, daemon=True).start()
        return s

    def command(self, cmd, args=None, kind='action'):
        c = self.clients.get('_commandPipe')
        if not c: raise RuntimeError('previewer command socket not connected')
        c.sendall(json.dumps({'version': '1.0.1', 'type': kind, 'command': cmd, 'args': args or {}}).encode() + b'\0')

    async def start(self):
        self.servers = [self.listen('_commandPipe'), self.listen('_imagePipe'), self.listen('-trace')]
        bin_dir = pathlib.Path(os.environ['PREVIEWER_BIN'])
        cmd = [str(bin_dir / 'Previewer'), '-j', os.environ['PREVIEW_ABC_DIR'], '-n', self.name, '-s', self.name,
               '-ts', self.name + '-trace', '-or', str(self.width), str(self.height), '-cr', str(self.width), str(self.height),
               '-device', 'phone', '-shape', 'rect', '-url', self.page, '-pm', 'Stage', '-av', 'ACE_2_0',
               '-sd', str(self.density), '-l', 'zh_CN', '-cm', self.color, '-o', 'portrait' if self.height > self.width else 'landscape',
               '-sm', 'dynamic', '-lws', str(self.port), '-arp', str(self.build / 'res'),
               '-ljPath', str(self.build / 'loader.json'), '-bn', os.environ.get('PREVIEW_BUNDLE', 'com.example.harmonyagent')]
        env = dict(os.environ, LD_LIBRARY_PATH=os.environ['PREVIEWER_LIBS'] + ':' + str(bin_dir),
                   LIBGL_ALWAYS_SOFTWARE='1', LD_AUDIT=os.environ['PREVIEWER_AUDIT'], LD_PRELOAD=os.environ['PREVIEWER_SHIM'])
        env.setdefault('DISPLAY', ':97')
        self.log = (self.report / 'previewer.log').open('wb')
        self.proc = subprocess.Popen(cmd, cwd=bin_dir, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, start_new_session=True)
        threading.Thread(target=self.pump, daemon=True).start()
        for _ in range(200):
            try: self.ws = await connect('ws://127.0.0.1:%d' % self.port, max_size=30_000_000, proxy=None); break
            except OSError:
                if self.proc.poll() is not None: raise RuntimeError('previewer exited early (%s); see previewer.log' % self.proc.returncode)
                await asyncio.sleep(0.1)
        else: raise RuntimeError('previewer image socket did not open')
        self.frame_task = asyncio.create_task(self.frames_loop())

    def pump(self):
        size = 0
        for line in self.proc.stdout:
            if size < 30_000_000: self.log.write(line); size += len(line)
            if any(p in line for p in ERROR_PATTERNS): self.errors.append(line.decode('utf-8', 'replace').strip()[:400])
            if b'[JsEngine Crash]Engine Crash Info End.' in line:
                self.crashed = True; os.killpg(self.proc.pid, signal.SIGKILL); break
        try: self.log.flush()
        except ValueError: pass  # closed by stop()

    async def frames_loop(self):
        try:
            async for data in self.ws:
                if isinstance(data, bytes):
                    pos = data.find(b'\xff\xd8\xff')
                    if pos >= 0: self.frame = data[pos:]; self.frames += 1
        except Exception: pass

    async def stop(self):
        self.stopped.set()
        if self.proc.poll() is None: os.killpg(self.proc.pid, signal.SIGKILL)
        self.proc.wait(); self.log.close()

    async def inspect(self):
        self.tree_event.clear(); self.command('inspector')
        for _ in range(50):
            if self.tree_event.is_set(): return self.tree
            await asyncio.sleep(0.05)
        raise RuntimeError('inspector did not answer')

    def nodes(self):
        found = []
        def walk(n):
            if isinstance(n, dict):
                attrs = n.get('$attrs', {})
                text = attrs.get('content') or attrs.get('label') or attrs.get('text') or attrs.get('value') or ''
                rect = [float(v) for v in n.get('$rect', '0,0,0,0').split(',')] if n.get('$rect') else [0, 0, 0, 0]
                found.append({'type': n.get('$type'), 'id': attrs.get('id', ''), 'text': text if isinstance(text, str) else '',
                              'placeholder': attrs.get('placeholder', ''), 'a11y': attrs.get('accessibilityText', '') or '',
                              'rect': rect, 'enabled': attrs.get('enabled', True) not in (False, 'false')})
                for c in n.get('$children', []): walk(c)
        walk(self.tree or {}); return found

    def find(self, target):
        # Every given key must match; nodes with no size are not on screen and are skipped.
        for n in self.nodes():
            if n['rect'][2] <= 0 or n['rect'][3] <= 0: continue
            if 'id' in target and n['id'] != target['id']: continue
            if 'text' in target and n['text'] != target['text']: continue
            if 'contains' in target and target['contains'] not in n['text']: continue
            if 'placeholder' in target and target['placeholder'] not in n['placeholder']: continue
            if 'a11y' in target and n['a11y'] != target['a11y']: continue
            if 'a11yContains' in target and target['a11yContains'] not in n['a11y']: continue
            if 'type' in target and n['type'] != target['type']: continue
            if 'enabled' in target and n['enabled'] != target['enabled']: continue
            return n
        return None

    async def tap(self, node):
        # Inspector rects are "x,y,width,height" in pixels.
        x, y = node['rect'][0] + node['rect'][2] / 2, node['rect'][1] + node['rect'][3] / 2
        self.command('MousePress', {'x': int(x), 'y': int(y)}); await asyncio.sleep(0.06)
        self.command('MouseRelease', {'x': int(x), 'y': int(y)})

    # The previewer's input-method path does not reach text fields, so letters are sent as hardware
    # key presses (KEYCODE_A = 2017 ... KEYCODE_Z, KEYCODE_0 = 2000 ..., SPACE = 2050). ASCII only.
    KEYCODES = dict([(chr(ord('a') + i), 2017 + i) for i in range(26)] + [(str(i), 2000 + i) for i in range(10)] + [(' ', 2050)])
    async def type(self, text):
        for ch in text.lower():
            code = self.KEYCODES.get(ch)
            if code is None: raise AssertionError('type supports a-z, 0-9 and space only: %r' % ch)
            for action in (0, 1):
                self.command('KeyPress', {'isInputMethod': False, 'keyCode': code, 'keyAction': action,
                                          'keyString': ch, 'pressedCodes': [code]})
                await asyncio.sleep(0.02)

    def screenshot(self, name):
        if self.frame: (self.report / (name + '.jpg')).write_bytes(self.frame)
        if self.tree is not None: (self.report / (name + '-tree.json')).write_text(json.dumps(self.tree, ensure_ascii=False, indent=1))

async def run(build, scenario_file, report):
    scenario = json.loads(pathlib.Path(scenario_file).read_text())
    report = pathlib.Path(report) / scenario['name']; report.mkdir(parents=True, exist_ok=True)
    p = Preview(build, scenario['page'], report, **scenario.get('screen', {}))
    results, ok = [], True
    await p.start()
    try:
        for i, step in enumerate(scenario['steps'], 1):
            label, error, before = json.dumps(step, ensure_ascii=False), None, len(p.errors)
            try:
                timeout = step.get('timeout', 8)
                if 'wait' in step: await asyncio.sleep(step['wait'])
                elif 'waitFor' in step or 'expect' in step:
                    target = step.get('waitFor') or step.get('expect'); deadline = time.time() + timeout
                    while True:
                        try:
                            await p.inspect()
                            if p.find(target): break
                        except RuntimeError:
                            if p.crashed or p.proc.poll() is not None: raise
                        # Startup can take a few seconds; keep polling until the step timeout.
                        if time.time() > deadline: raise AssertionError('not found: %s' % json.dumps(target, ensure_ascii=False))
                        await asyncio.sleep(0.3)
                elif 'expectMissing' in step:
                    await asyncio.sleep(0.5); await p.inspect()
                    if p.find(step['expectMissing']): raise AssertionError('still visible: %s' % json.dumps(step['expectMissing'], ensure_ascii=False))
                elif 'tap' in step:
                    await p.inspect(); node = p.find(step['tap'])
                    if not node: raise AssertionError('tap target not found: %s' % json.dumps(step['tap'], ensure_ascii=False))
                    tree_before = json.dumps(p.tree, sort_keys=True)
                    await p.tap(node); await asyncio.sleep(step.get('settle', 0.8)); await p.inspect()
                    if step.get('expectChange', True) and json.dumps(p.tree, sort_keys=True) == tree_before:
                        raise AssertionError('tap did not change the page (unresponsive?)')
                elif 'type' in step: await p.type(step['type']); await asyncio.sleep(0.5)
                elif 'screenshot' in step: await asyncio.sleep(0.6); await p.inspect(); p.screenshot(step['screenshot'])
                if p.crashed: raise AssertionError('previewer crashed')
                if len(p.errors) > before: raise AssertionError('application error: ' + p.errors[before])
            except Exception as e:
                error, ok = str(e), False
                try: await p.inspect()
                except Exception: pass
                p.screenshot('failed-step-%02d' % i)
            results.append({'step': i, 'action': label, 'ok': error is None, 'error': error})
            print(('PASS ' if error is None else 'FAIL ') + '%02d %s%s' % (i, label, '' if error is None else '  -> ' + error), flush=True)
            if error and not step.get('continueOnFailure'): break
    finally:
        await p.stop()
    (report / 'result.json').write_text(json.dumps({'scenario': scenario['name'], 'ok': ok, 'steps': results}, ensure_ascii=False, indent=1))
    return ok

if __name__ == '__main__':
    sys.exit(0 if asyncio.run(run(*sys.argv[1:4])) else 1)
