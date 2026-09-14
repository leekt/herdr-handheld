(async () => {
  'use strict';
  let generation = -1, ready = false;
  let term, fit;
  const createTerminal = () => {
  const instance = new Terminal({
    fontSize: 17, fontFamily: '"JetBrains Mono", monospace', lineHeight: 1.12,
    scrollback: 600, cursorBlink: false, disableStdin: true, convertEol: false,
    allowProposedApi: false,
    theme: { background:'#000000',foreground:'#e4e9f0',cursor:'#ffb020',
      black:'#000000',red:'#ff9498',green:'#88d4ae',yellow:'#f2cc80',blue:'#91bfff',
      magenta:'#c9a9ef',cyan:'#89d8df',white:'#e4e9f0',brightBlack:'#8e9bab' }
  });
  fit = new FitAddon.FitAddon(); instance.loadAddon(fit);
  instance.attachCustomKeyEventHandler(() => false);
  instance.onData(() => {}); instance.onBinary(() => {});
  instance.parser.registerOscHandler(52, () => true);
  instance.parser.registerOscHandler(8, () => true);
  instance.parser.registerOscHandler(1337, () => true);
  instance.open(document.getElementById('terminal'));
  return instance;
  };
  const send = obj => {
    if (window.Handheld) window.Handheld.postMessage(JSON.stringify(obj));
  };
  // This renderer is output-only: native controls exclusively own the input path.
  await document.fonts.load('17px "JetBrains Mono"');
  term=createTerminal();
  let sizeTimer;
  const measure = () => {
    clearTimeout(sizeTimer);
    sizeTimer = setTimeout(() => {
      const d = fit.proposeDimensions();
      if (d && d.cols >= 2 && d.rows >= 2) send({type:'viewport',cols:Math.min(d.cols,500),rows:Math.min(d.rows,300),generation});
    }, 120);
  };
  new ResizeObserver(measure).observe(document.getElementById('terminal'));
  window.addEventListener('message', event => {
    let msg;
    try { msg=JSON.parse(event.data); } catch { return; }
    if (msg.type==='reset') {
      generation=msg.generation;
      term.dispose(); document.getElementById('terminal').replaceChildren();
      term=createTerminal();
      term.options.fontSize=Math.max(12,Math.min(26,msg.fontSize || 17));
      ready=true; measure(); send({type:'reset',generation}); return;
    }
    if (!ready || msg.generation!==generation) return;
    if (msg.type==='frame' && typeof msg.bytes==='string' && msg.bytes.length<=1400000) {
      const g=generation, active=term;
      active.resize(msg.width,msg.height);
      const raw=atob(msg.bytes); const bytes=Uint8Array.from(raw,c=>c.charCodeAt(0));
      active.write(bytes,()=> {
        if(g===generation) send({type:'written',seq:msg.seq,generation:g,
          applicationCursor:term.modes.applicationCursorKeysMode,bracketedPaste:term.modes.bracketedPasteMode});
      });
    } else if (msg.type==='scroll') {
      term.scrollLines(Math.max(-100,Math.min(100,msg.lines || 0)));
    } else if (msg.type==='font') {
      term.options.fontSize=Math.max(12,Math.min(26,msg.size)); measure();
    }
  });
  document.addEventListener('click', e=>e.preventDefault(),true);
  document.addEventListener('contextmenu',e=>e.preventDefault());
  send({type:'ready'});
})();
