#!/usr/bin/env python3
"""Temporary, loopback-only SSH test fixture. Not part of the Android product.
Runs no shell, exposes no filesystem, accepts only a generated disposable key.
"""
import asyncio, asyncssh, json, pathlib, base64, shlex, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
DEST=ROOT/'.tools/ssh-fixture'
DEST.mkdir(exist_ok=True)
ASSETS=DEST/'assets';ASSETS.mkdir(exist_ok=True)
hostkey=asyncssh.generate_private_key('ssh-ed25519')
clientkey=asyncssh.generate_private_key('ssh-ed25519')
(ASSETS/'ssh-fixture-key').write_bytes(clientkey.export_private_key('openssh'))
(ASSETS/'ssh-fixture.json').write_text(json.dumps({'host':'127.0.0.1','port':18422,'fingerprint':hostkey.get_fingerprint('sha256')}))
class Server(asyncssh.SSHServer):
    def begin_auth(self,username):return True
    def public_key_auth_supported(self):return True
    def validate_public_key(self,username,key):return username=='fixture' and key==clientkey.convert_to_public()
async def process(p):
    args=shlex.split(p.command or '')
    if args==['probe']:
        p.stdout.write(b'{"ok":true}\n');p.stderr.write(b'fixture diagnostic\n');p.exit(0);return
    prefix=['herdr','--session','fixture']
    if args[:3]!=prefix:p.exit(2);return
    args=args[3:]
    if args==['--version']:p.stdout.write(b'herdr 0.9.0\n')
    elif args==['status']:p.stdout.write(b'client:\n  version: 0.9.0\nserver:\n  status: running\n  version: 0.9.0\n  endpoint_compatible: yes\n')
    elif args==['terminal','session','--help']:p.stdout.write(b'Commands:\n  observe\n  control\n')
    elif args==['api','schema','--json']:p.stdout.write(b'{"schema_version":1,"protocol":22,"schemas":{}}\n')
    elif args==['agent','list']:p.stdout.write((ROOT/'fixtures/herdr/0.9.0/agents.json').read_bytes())
    elif len(args)==9 and args[:2]==['pane','read'] and args[3:]==['--source','recent-unwrapped','--lines','160','--format','text']:
        p.stdout.write((ROOT/'fixtures/herdr/0.9.0/pane-read.txt').read_bytes())
    elif args[:2]==['terminal','session'] and len(args)==8 and args[2] in ['observe','control']:
        cols=int(args[5]);rows=int(args[7]);seq=0
        async def frame(text):
            nonlocal seq
            seq+=1
            data=json.dumps({'type':'terminal.frame','seq':seq,'encoding':'ansi','width':cols,'height':rows,'full':True,'bytes':base64.b64encode(text).decode()}).encode()+b'\n'
            # Deliberately split envelopes at arbitrary boundaries.
            for i in range(0,len(data),7):p.stdout.write(data[i:i+7]);await asyncio.sleep(.001)
        await frame(b'\x1b[2J\x1b[HSSH fixture ready')
        try:
            async for line in p.stdin:
                obj=json.loads(line)
                if obj.get('type')=='terminal.release':break
                if obj.get('type')=='terminal.input' and args[2]=='control':
                    await frame(b'\x1b[2J\x1b[H'+base64.b64decode(obj['bytes']))
        except (asyncssh.BreakReceived,asyncssh.TerminalSizeChanged,asyncssh.SignalReceived):pass
    else:p.exit(2);return
    p.exit(0)
async def main():
    async with await asyncssh.create_server(Server,'127.0.0.1',18422,server_host_keys=[hostkey],process_factory=process,encoding=None):
        print('Disposable SSH fixture listening on loopback port 18422',flush=True)
        await asyncio.Future()
try:asyncio.run(main())
except KeyboardInterrupt:pass
