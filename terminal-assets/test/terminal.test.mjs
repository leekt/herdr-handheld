import test from 'node:test';
import assert from 'node:assert/strict';
import headless from '@xterm/headless';
const { Terminal } = headless;
const write=(term,bytes)=>new Promise(resolve=>term.write(bytes,resolve));
test('UTF-8 split at every byte preserves Korean, emoji, combining marks',async()=>{
  const bytes=new TextEncoder().encode('한글 👋 e\u0301');
  for(let split=0;split<=bytes.length;split++) {
    const term=new Terminal({cols:40,rows:4,allowProposedApi:true});
    await write(term,bytes.slice(0,split));await write(term,bytes.slice(split));
    assert.equal(term.buffer.active.getLine(0).translateToString(true),'한글 👋 e\u0301');term.dispose();
  }
});
test('ANSI cursor moves, clear, colors and alternate screen',async()=>{
  const term=new Terminal({cols:40,rows:4,allowProposedApi:true});
  await write(term,'normal\x1b[?1049h\x1b[31malternate\x1b[0m');
  assert.equal(term.buffer.active.type,'alternate');
  await write(term,'\x1b[2J\x1b[H한글');
  assert.equal(term.buffer.active.getLine(0).translateToString(true),'한글');
  await write(term,'\x1b[?1049l');
  assert.equal(term.buffer.active.getLine(0).translateToString(true),'normal');term.dispose();
});
test('OSC clipboard and link handlers consume control sequences',async()=>{
  const term=new Terminal({cols:40,rows:4,allowProposedApi:true});let clipboard=0;
  term.parser.registerOscHandler(52,()=>{clipboard++;return true});
  term.parser.registerOscHandler(8,()=>true);
  await write(term,'\x1b]52;c;c2VjcmV0\x07safe\x1b]8;;https://example.com\x07 link\x1b]8;;\x07');
  assert.equal(clipboard,1);assert.equal(term.buffer.active.getLine(0).translateToString(true),'safe link');term.dispose();
});
