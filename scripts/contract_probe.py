#!/usr/bin/env python3
"""Exercise the installed CLI using a disposable, isolated Herdr session.
Never connects to the default session. Saves only synthetic terminal contents.
"""
import base64, json, os, pathlib, select, shutil, subprocess, tempfile, time

ROOT=pathlib.Path(__file__).resolve().parents[1]
binary=shutil.which('herdr')
assert binary, 'Install Herdr yourself before running contract probes.'
temporary=pathlib.Path(tempfile.mkdtemp(prefix='rg-contract-',dir='/tmp'))
environment={k:v for k,v in os.environ.items() if not k.startswith('HERDR_')}
environment['HERDR_CONFIG_PATH']=str(temporary/'config.toml')
(temporary/'config.toml').write_text('')
session='handheld-contract'
prefix=[binary,'--session',session]
def cli(*args):
    r=subprocess.run(prefix+list(args),env=environment,capture_output=True,timeout=10)
    if r.returncode: raise RuntimeError(f'CLI failed: {args[0:2]} (exit {r.returncode})')
    return json.loads(r.stdout) if r.stdout.strip() else None
def stream(mode,terminal,cols=44,rows=18):
    return subprocess.Popen(prefix+['terminal','session',mode,terminal,'--cols',str(cols),'--rows',str(rows)],env=environment,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,bufsize=0)
def record(p,timeout=5):
    deadline=time.monotonic()+timeout;line=bytearray()
    while time.monotonic()<deadline:
        if not select.select([p.stdout],[],[],max(0,deadline-time.monotonic()))[0]:break
        b=p.stdout.read(1)
        if not b:break
        if b==b'\n':return json.loads(line)
        line.extend(b)
    raise TimeoutError('No complete terminal record')
def send(p,payload):
    p.stdin.write((json.dumps(payload)+'\n').encode());p.stdin.flush()
server=None;streams=[]
try:
    log=open(temporary/'server.log','wb')
    server=subprocess.Popen(prefix+['server'],env=environment,stdin=subprocess.DEVNULL,stdout=log,stderr=log)
    for _ in range(100):
        if (temporary/'sessions'/session/'herdr.sock').exists():break
        if server.poll() is not None:raise RuntimeError('Isolated test server exited')
        time.sleep(.05)
    work=cli('workspace','create','--cwd',str(temporary),'--label','Handheld contract fixture','--no-focus')
    pane=work['result']['root_pane']['pane_id']
    info=cli('pane','get',pane)
    (temporary/'pane-shape.json').write_text(json.dumps(info,indent=2))
    # Capture bytes in raw mode and render an entirely synthetic ASCII/Korean screen.
    program=temporary/'fixture.py'
    program.write_text('''import os,sys,termios,tty,base64,json,signal
saved=termios.tcgetattr(0);tty.setraw(0)
def size(*args):
 s=os.get_terminal_size()
 with open('sizes.jsonl','a') as f:f.write(json.dumps([s.columns,s.lines])+'\\n')
 print('\\r\\nSIZE %d %d\\r'%(s.columns,s.lines),flush=True)
signal.signal(signal.SIGWINCH,size)
try:
 print('\\x1b[?2004h\\x1b[2J\\x1b[HHandheld contract fixture\\r\\n한글 👋 e\\u0301\\r\\n',end='',flush=True)
 size()
 while True:
  data=os.read(0,4096)
  with open('input.jsonl','a') as f:f.write(json.dumps({'bytes':base64.b64encode(data).decode()})+'\\n')
  print('\\r\\nINPUT '+data.hex()+'\\r',flush=True)
finally:termios.tcsetattr(0,termios.TCSADRAIN,saved)
''')
    cli('pane','run',pane,'python3 '+str(program))
    time.sleep(.3)
    initial_sizes=(temporary/'sizes.jsonl').read_text()
    observe=stream('observe',pane);streams.append(observe)
    frame=record(observe);assert frame['type']=='terminal.frame' and frame['full'] is True
    assert(frame['width'],frame['height'])==(44,18)
    small=stream('observe',pane,30,12);streams.append(small)
    smallframe=record(small);assert(smallframe['width'],smallframe['height'])==(30,12)
    assert (temporary/'sizes.jsonl').read_text()==initial_sizes,'Observers must not resize the underlying PTY'
    controller=stream('control',pane);streams.append(controller)
    assert record(controller)['type']=='terminal.frame'
    conflict=stream('control',pane);streams.append(conflict)
    closed=record(conflict);assert closed['type']=='terminal.closed'
    assert 'already' in (closed.get('reason') or '').lower() or 'owner' in (closed.get('reason') or '').lower() or 'attached' in (closed.get('reason') or '').lower(),closed
    text='한글\nEnglish'
    paste=('\x1b[200~'+text+'\x1b[201~').encode()
    send(controller,{'type':'terminal.input','bytes':base64.b64encode(paste).decode()})
    send(controller,{'type':'terminal.input','bytes':base64.b64encode(b'\r').decode()})
    time.sleep(.2)
    captured=b''.join(base64.b64decode(json.loads(line)['bytes']) for line in (temporary/'input.jsonl').read_text().splitlines())
    assert captured==paste+b'\r',(captured,paste)
    send(controller,{'type':'terminal.resize','cols':38,'rows':16})
    send(controller,{'type':'terminal.scroll','direction':'up','lines':2,'source':'wheel'})
    send(controller,{'type':'terminal.release'})
    controller.wait(timeout=5)
    # EOF releases only the bridge, and a fresh controller can acquire the same target.
    again=stream('control',pane);streams.append(again);assert record(again)['type']=='terminal.frame'
    again.stdin.close();again.wait(timeout=5)
    assert cli('pane','get',pane)['result']
    result={'cli':'0.9.0','session':session,'checks':['two independent observer viewports','observers do not resize the underlying PTY','initial full frame','exclusive controller conflict','Korean multiline paste plus Enter exact bytes','resize and scroll accepted','release leaves pane alive','stdin EOF leaves pane alive'],
        'observer_frame':{k:v for k,v in frame.items() if k!='bytes'},'conflict_record':closed,
        'source':'installed CLI + isolated synthetic test session','tested_at':time.strftime('%Y-%m-%dT%H:%M:%S%z')}
    out=ROOT/'artifacts'/'contract-probe.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
    (ROOT/'fixtures/herdr/0.9.0'/'captured-synthetic-frame.ndjson').write_text(json.dumps(frame)+'\n')
    (ROOT/'fixtures/herdr/0.9.0'/'control-conflict.ndjson').write_text(json.dumps(closed)+'\n')
    print(json.dumps(result,indent=2))
finally:
    for p in streams:
        if p.poll() is None:
            p.terminate()
            try:p.wait(timeout=3)
            except subprocess.TimeoutExpired:p.kill()
    if server is not None:
        subprocess.run(prefix+['server','stop'],env=environment,capture_output=True,timeout=5)
        try:server.wait(timeout=5)
        except subprocess.TimeoutExpired:server.terminate()
    print('Isolated test files:',temporary)
