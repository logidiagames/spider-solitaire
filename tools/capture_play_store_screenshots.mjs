import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const adb = '/opt/homebrew/share/android-commandlinetools/platform-tools/adb';
const packageName = 'com.nexusplaylabss.freecell';
const activity = `${packageName}/.MainActivity`;
const outputDir = join(process.cwd(), 'play-store-screenshots-preview');
const remotePrefs = '/data/local/tmp/freecell_store_prefs.xml';

const captures = [
  ['01-classic', 'classic', 'classic'],
  ['02-aurora', 'aurora', 'aurora'],
  ['03-sunlight', 'sunlight', 'sunlight'],
  ['04-neon', 'neon', 'neon'],
  ['05-moonstone', 'moonstone', 'moonstone'],
  ['06-emberwood', 'emberwood', 'emberwood'],
  ['07-classic-aurora-cards', 'classic', 'aurora'],
];
const firstCapture = Math.max(0, Number(process.argv[2] ?? 1) - 1);
const lastCapture = Math.min(captures.length, Number(process.argv[3] ?? captures.length));

function run(args, options = {}) {
  return execFileSync(adb, args, {
    encoding: options.binary ? null : 'utf8',
    maxBuffer: 32 * 1024 * 1024,
    stdio: options.binary ? ['ignore', 'pipe', 'inherit'] : 'inherit',
  });
}

function sleep(milliseconds) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, milliseconds);
}

function prefsXml(tableThemeName, cardThemeName) {
  const state = JSON.stringify({
    version: 1,
    currentLanguage: 'en',
    tableThemeName,
    cardThemeName,
  }).replaceAll('&', '&amp;').replaceAll('"', '&quot;');

  return `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n  <boolean name="first_tutorial_completed" value="true" />\n  <string name="saved_state">${state}</string>\n</map>\n`;
}

mkdirSync(outputDir, { recursive: true });
run(['shell', 'cmd', 'connectivity', 'airplane-mode', 'enable']);
run(['shell', 'input', 'keyevent', '82']);
run(['shell', 'wm', 'dismiss-keyguard']);
run(['shell', 'settings', 'put', 'global', 'window_animation_scale', '0']);
run(['shell', 'settings', 'put', 'global', 'transition_animation_scale', '0']);
run(['shell', 'settings', 'put', 'global', 'animator_duration_scale', '0']);

for (const [filename, tableThemeName, cardThemeName] of captures.slice(firstCapture, lastCapture)) {
  const localPrefs = join('/private/tmp', `${filename}-freecell-prefs.xml`);
  writeFileSync(localPrefs, prefsXml(tableThemeName, cardThemeName));

  run(['shell', 'am', 'force-stop', packageName]);
  run(['push', localPrefs, remotePrefs]);
  run([
    'shell',
    `run-as ${packageName} sh -c 'mkdir -p shared_prefs && cp ${remotePrefs} shared_prefs/freecell_web_state.xml'`,
  ]);
  run(['shell', 'am', 'start', '-W', '-n', activity]);
  sleep(5500);

  run(['shell', 'cmd', 'connectivity', 'airplane-mode', 'disable']);
  sleep(700);
  const png = run(['exec-out', 'screencap', '-p'], { binary: true });
  writeFileSync(join(outputDir, `${filename}.png`), png);
  run(['shell', 'cmd', 'connectivity', 'airplane-mode', 'enable']);
  sleep(1500);
}

