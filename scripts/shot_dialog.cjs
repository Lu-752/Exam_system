/* 临时脚本：打开建题弹窗 → 切题冒题 → 截图（用本机 Edge，无需下载 Chromium） */
const { chromium } = require('playwright-core');

(async () => {
  const browser = await chromium.launch({
    executablePath: 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    headless: true
  });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200 } });
  await page.goto('http://127.0.0.1:5173/#/questions?demo=1');
  await page.waitForTimeout(2500);

  await page.getByRole('button', { name: '新建试题' }).click();
  await page.waitForTimeout(600);

  // 切换题型为「题冒题」
  await page.locator('.el-radio-button', { hasText: '题冒题' }).click();
  await page.waitForTimeout(600);

  // 填材料 + 第一个子题
  await page.locator('.el-dialog textarea').first().fill('【演示材料】某仓库发生火情，值班员先拍照再扑救。');
  await page.locator('.sub-card').first().locator('textarea').fill('仓库初起火灾应优先使用（ ）。');
  // 添加一个问答子题
  await page.getByRole('button', { name: '+ 添加子题' }).click();
  await page.waitForTimeout(400);
  const cards = page.locator('.sub-card');
  await cards.nth(1).locator('textarea').fill('请指出值班员处置不当之处。');

  await page.screenshot({ path: 'shots/dialog-composite.png' });
  await browser.close();
  console.log('saved shots/dialog-composite.png');
})().catch((e) => { console.error(e.message); process.exit(1); });
