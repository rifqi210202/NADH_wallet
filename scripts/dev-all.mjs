import { spawn } from 'node:child_process';

const commands = [
  ['npm', ['run', 'dev:api']],
  ['npm', ['run', 'dev']],
];

const children = commands.map(([command, args]) => {
  const child = spawn(command, args, {
    stdio: 'inherit',
    shell: true,
    env: process.env,
  });

  child.on('exit', (code) => {
    if (code && code !== 0) {
      process.exitCode = code;
    }
  });

  return child;
});

function shutdown() {
  for (const child of children) {
    child.kill();
  }
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
