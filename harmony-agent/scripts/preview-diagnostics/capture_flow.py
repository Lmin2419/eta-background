import json,time,urllib.request,pathlib
base='http://127.0.0.1:8877'
events=[]
def post(path,data):
    req=urllib.request.Request(base+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
    result=json.load(urllib.request.urlopen(req))
    assert result.get('ok'),result
    return result
def nodes():return json.load(urllib.request.urlopen(base+'/state'))['nodes']
def find(text=None,resource=None,id=None,kind=None):
    for n in nodes():
        if kind and n['type']!=kind:continue
        if text is not None and n['text']!=text:continue
        if resource and not n['resource'].endswith(resource):continue
        if id and n['attrs'].get('id')!=id:continue
        return n
    return None
def wait(**selector):
    for _ in range(70):
        n=find(**selector)
        if n:return n
        time.sleep(.12)
    raise AssertionError('Missing native UI node '+str(selector))
def tap(**selector):
    n=wait(**selector);x,y,w,h=map(float,n['rect'].split(','))
    post('/tap',{'x':round(x+w/2),'y':round(y+h/2)})
    events.append({'tap':selector,'rect':n['rect']});time.sleep(.15)
def capture(name):
    time.sleep(.2);post('/capture',{'name':name});events.append({'capture':name})
def record():pathlib.Path('/workspace/harmony-agent/dist/screenshots-native/flow.json').write_text(json.dumps(events,ensure_ascii=False,indent=2))
try:
    wait(text='有什么可以帮你？');capture('01-home')
    tap(text='整理思路');assert '帮我整理' in wait(id='foundation-composer')['text'];capture('02-compose')
    tap(resource='ic_send.svg');wait(resource='ic_stop.svg');time.sleep(.3);capture('03-streaming')
    wait(text='可以继续切换会话、编辑模型或查看侧栏。');capture('04-chat')
    tap(resource='ic_menu.svg');wait(text='最近对话');capture('05-sidebar')
    tap(text='模型服务');wait(text='示例模型服务');capture('06-providers')
    tap(text='示例模型服务');wait(id='model-name');capture('07-provider-config')
    before={key:wait(id=key)['text'] for key in ['model-name','model-address','model-key','model-id']}
    tap(text='测试连接并获取模型');wait(text='添加远端模型');capture('08-catalog')
    tap(text='新增模型 A');tap(text='新增模型 B');capture('09-catalog-selected')
    tap(text='添加所选 · 2');wait(text='新增模型 A');capture('10-provider-models')
    tap(text='配置',kind='Button');wait(id='model-name')
    after={key:wait(id=key)['text'] for key in before};assert after==before,(before,after)
    events.append({'assertion':'Text connection / catalog import preserves every form value','passed':True})
    capture('11-config-preserved')
    tap(text='保存服务');time.sleep(.3)
    print('Native touch flow completed; captures are saved.',flush=True)
finally:record()
