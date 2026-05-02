const { execSync } = require('child_process');

function run(command) {
  return execSync(command, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
}

function toProcessName(name) {
  return name.replace(/\.exe$/i, '');
}

function isProcessRunning(name) {
  const processName = toProcessName(name);
  try {
    execSync(
      `powershell -NoProfile -Command "if (Get-Process -Name '${processName}' -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }"`,
      { stdio: 'ignore' }
    );
    return true;
  } catch {
    return false;
  }
}

function killByImage(name, label) {
  const processName = toProcessName(name);
  if (!isProcessRunning(name)) {
    console.log(`[skip] ${label} is not running`);
    return;
  }
  try {
    execSync(`powershell -NoProfile -Command "Stop-Process -Name '${processName}' -Force"`, {
      stdio: 'ignore'
    });
    console.log(`[ok] stopped ${label}`);
  } catch {
    console.log(`[warn] failed to stop ${label}`);
  }
}

function killPciSpringBoot() {
  try {
    const output = run(
      `powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \\"name = 'java.exe'\\" | Select-Object ProcessId, CommandLine | ConvertTo-Json -Compress"`
    ).trim();

    if (!output) {
      console.log('[skip] PCI Spring Boot is not running');
      return;
    }

    const parsed = JSON.parse(output);
    const items = Array.isArray(parsed) ? parsed : [parsed];
    const hits = items.filter((item) =>
      /com\.pci\.PciApplication|PersonalContextInjection\\backend|pci-backend/i.test(item.CommandLine || '')
    );

    if (hits.length === 0) {
      console.log('[skip] PCI Spring Boot is not running');
      return;
    }

    for (const item of hits) {
      execSync(`powershell -NoProfile -Command "Stop-Process -Id ${item.ProcessId} -Force"`, {
        stdio: 'ignore'
      });
    }
    console.log('[ok] stopped PCI Spring Boot');
  } catch {
    console.log('[warn] failed to stop PCI Spring Boot');
  }
}

function stopRedis() {
  if (!isProcessRunning('redis-server.exe')) {
    console.log('[skip] Redis is not running');
    return;
  }

  try {
    execSync('D:\\redis\\redis-cli.exe shutdown nosave', { stdio: 'ignore' });
  } catch {
    // ignore and fallback
  }

  if (isProcessRunning('redis-server.exe')) {
    killByImage('redis-server.exe', 'Redis');
  } else {
    console.log('[ok] stopped Redis');
  }
}

console.log('=== Stop PCI Services ===');
killPciSpringBoot();
killByImage('qdrant.exe', 'Qdrant');
stopRedis();
console.log('[info] frontend dev server can be stopped with stop-frontend.bat');
console.log('=== Done ===');
