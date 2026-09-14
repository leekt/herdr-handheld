import { mkdir, copyFile } from 'node:fs/promises';
const destination = new URL('../app/src/main/assets/terminal/', import.meta.url);
await mkdir(destination, { recursive: true });
for (const [from, to] of [
  ['node_modules/@xterm/xterm/lib/xterm.js', 'xterm.js'],
  ['node_modules/@xterm/xterm/css/xterm.css', 'xterm.css'],
  ['node_modules/@xterm/addon-fit/lib/addon-fit.js', 'addon-fit.js'],
  ['../app/src/main/res/font/jetbrains_mono.ttf', 'jetbrains_mono.ttf'],
  ['index.html', 'index.html'], ['terminal.js', 'terminal.js'], ['terminal.css', 'terminal.css']
]) await copyFile(new URL(from, import.meta.url), new URL(to, destination));
console.log('Bundled local terminal assets.');
