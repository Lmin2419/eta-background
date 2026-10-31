"""Drive the OpenHarmony Linux Previewer with scenario files: tap, long press, swipe, back, type, wait, assert, screenshot.
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
                              'rect': rect, 'enabled': attrs.get('enabled', True) not in (False, 'false'), 'attrs': attrs})
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
            if 'attrs' in target and any(str(n['attrs'].get(k)) != str(v) for k, v in target['attrs'].items()): continue
            return n
        return None

    INTERACTIVE = {'Button', 'TextInput', 'TextArea', 'Search', 'Toggle', 'Checkbox', 'Radio', 'Slider', 'Select'}
    def elements(self):
        """Visible nodes that a selector can target, one per line: type | id | text | a11y | placeholder | enabled | rect."""
        lines, seen = [], set()
        for n in self.nodes():
            if n['rect'][2] <= 0 or n['rect'][3] <= 0: continue
            if not (n['id'] or n['text'] or n['a11y'] or n['placeholder'] or n['type'] in self.INTERACTIVE): continue
            row = ' | '.join([n['type'] or '?', 'id=' + n['id'] if n['id'] else '', repr(n['text'][:40]) if n['text'] else '',
                              'a11y=' + n['a11y'] if n['a11y'] else '', 'placeholder=' + n['placeholder'] if n['placeholder'] else '',
                              '' if n['enabled'] else 'disabled', '%d,%d %dx%d' % tuple(n['rect'])])
            if row not in seen: seen.add(row); lines.append(row)
        return lines

    def dump(self, name, limit=None):
        lines = self.elements()
        (self.report / (name + '-elements.txt')).write_text('\n'.join(lines) + '\n')
        shown = lines if limit is None else lines[:limit]
        print('   visible elements (%d%s), also in %s-elements.txt:' % (len(lines), '' if len(shown) == len(lines) else ', first %d' % len(shown), name))
        for line in shown: print('     ' + line)

    def center(self, node, offset=(0, 0)):
        return int(node['rect'][0] + node['rect'][2] / 2 + offset[0]), int(node['rect'][1] + node['rect'][3] / 2 + offset[1])

    async def long_press(self, node, hold, offset=(0, 0)):
        x, y = self.center(node, offset)
        self.command('MousePress', {'x': x, 'y': y}); await asyncio.sleep(hold)
        self.command('MouseRelease', {'x': x, 'y': y})

    async def swipe(self, x1, y1, x2, y2, duration=0.3):
        steps = 12
        self.command('MousePress', {'x': int(x1), 'y': int(y1)})
        for i in range(1, steps + 1):
            await asyncio.sleep(duration / steps)
            self.command('MouseMove', {'x': int(x1 + (x2 - x1) * i / steps), 'y': int(y1 + (y2 - y1) * i / steps)})
        self.command('MouseRelease', {'x': int(x2), 'y': int(y2)})

    async def tap(self, node, offset=(0, 0)):
        # Inspector rects are "x,y,width,height" in pixels; offset moves the point from the center, in pixels.
        x, y = self.center(node, offset)
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
                elif 'tap' in step or 'longPress' in step or 'swipe' in step or 'back' in step:
                    await p.inspect(); tree_before = json.dumps(p.tree, sort_keys=True)
                    if 'back' in step: p.command('BackClicked')
                    elif 'swipe' in step:
                        sw = step['swipe']; w, h = p.width, p.height
                        if 'on' in sw:
                            node = p.find(sw['on'])
                            if not node: raise AssertionError('swipe target not found: %s' % json.dumps(sw['on'], ensure_ascii=False))
                            x, y = p.center(node); r = node['rect']
                        else: x, y, r = w / 2, h / 2, [0, 0, w, h]
                        dx, dy = {'up': (0, -1), 'down': (0, 1), 'left': (-1, 0), 'right': (1, 0)}[sw['direction']]
                        dist = sw.get('distance', 0.6)
                        await p.swipe(x - dx * r[2] * dist / 2, y - dy * r[3] * dist / 2, x + dx * r[2] * dist / 2, y + dy * r[3] * dist / 2,
                                      sw.get('duration', 0.3))
                    else:
                        target = step.get('tap') or step.get('longPress'); node = p.find(target)
                        if not node: raise AssertionError('%s target not found: %s' % ('tap' if 'tap' in step else 'longPress', json.dumps(target, ensure_ascii=False)))
                        offset = tuple(step.get('offset', (0, 0)))
                        if 'tap' in step: await p.tap(node, offset)
                        else: await p.long_press(node, step.get('hold', 1.0), offset)
                    await asyncio.sleep(step.get('settle', 0.8)); await p.inspect()
                    if step.get('expectChange', True) and json.dumps(p.tree, sort_keys=True) == tree_before:
                        raise AssertionError('the page did not change after this step (unresponsive?)')
                elif 'dump' in step: await asyncio.sleep(0.6); await p.inspect(); p.screenshot(step['dump']); p.dump(step['dump'])
                elif 'type' in step: await p.type(step['type']); await asyncio.sleep(0.5)
                elif 'screenshot' in step: await asyncio.sleep(0.6); await p.inspect(); p.screenshot(step['screenshot'])
                if p.crashed: raise AssertionError('previewer crashed')
                if len(p.errors) > before: raise AssertionError('application error: ' + p.errors[before])
            except Exception as e:
                error, ok = str(e), False
                try: await p.inspect()
                except Exception: pass
                p.screenshot('failed-step-%02d' % i)
                print('FAIL %02d %s  -> %s' % (i, label, error), flush=True)
                try:
                    if p.tree is not None: p.dump('failed-step-%02d' % i, limit=60)
                except Exception: pass
                if p.errors[before:]: print('   application errors:\n     ' + '\n     '.join(p.errors[before:before + 5]))
            results.append({'step': i, 'action': label, 'ok': error is None, 'error': error})
            if error is None: print('PASS %02d %s' % (i, label), flush=True)
            if error and not step.get('continueOnFailure'): break
    finally:
        await p.stop()
    (report / 'result.json').write_text(json.dumps({'scenario': scenario['name'], 'ok': ok, 'steps': results}, ensure_ascii=False, indent=1))
    return ok

if __name__ == '__main__':
    sys.exit(0 if asyncio.run(run(*sys.argv[1:4])) else 1)
