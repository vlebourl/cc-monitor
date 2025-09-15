const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

// Create screenshots directory
const screenshotsDir = path.join(__dirname, 'screenshots');
if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir);
}

async function testClaudeCodeMonitorUI() {
    console.log('🚀 Starting Claude Code Monitor UI Testing with Playwright...');

    const browser = await chromium.launch({
        headless: true,
        slowMo: 300
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();

    try {
        // Navigate to the application
        console.log('📱 Navigating to http://localhost:3000...');
        await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });
        await page.waitForTimeout(2000);

        // Screenshot 1: Main page initial load
        console.log('📸 Taking screenshot: Main page initial load');
        await page.screenshot({
            path: path.join(screenshotsDir, '01-main-page-initial.png'),
            fullPage: true
        });

        // Wait for QR code to load
        console.log('⏳ Waiting for QR code generation...');
        await page.waitForSelector('#qrcode canvas, .status.error', { timeout: 15000 });
        await page.waitForTimeout(3000);

        // Screenshot 2: Main page with QR code
        console.log('📸 Taking screenshot: Main page with QR code');
        await page.screenshot({
            path: path.join(screenshotsDir, '02-main-page-with-qr.png'),
            fullPage: true
        });

        // Test QR code regeneration
        console.log('🔄 Testing QR code regeneration...');
        const refreshButton = await page.$('#refreshBtn');
        if (refreshButton) {
            await refreshButton.click();
            await page.waitForTimeout(3000);
        }

        // Screenshot 3: QR code regeneration process
        console.log('📸 Taking screenshot: QR code regeneration');
        await page.screenshot({
            path: path.join(screenshotsDir, '03-qr-regeneration.png'),
            fullPage: true
        });

        // Screenshot 4: Server status info
        console.log('📸 Taking screenshot: Server status information');
        await page.screenshot({
            path: path.join(screenshotsDir, '04-server-status.png'),
            fullPage: true
        });

        // Test mobile responsive design
        console.log('📱 Testing mobile responsive design...');
        await page.setViewportSize({ width: 375, height: 667 });
        await page.waitForTimeout(1000);

        // Screenshot 5: Mobile view
        console.log('📸 Taking screenshot: Mobile view');
        await page.screenshot({
            path: path.join(screenshotsDir, '05-mobile-view.png'),
            fullPage: true
        });

        // Test tablet view
        console.log('📱 Testing tablet responsive design...');
        await page.setViewportSize({ width: 768, height: 1024 });
        await page.waitForTimeout(1000);

        // Screenshot 6: Tablet view
        console.log('📸 Taking screenshot: Tablet view');
        await page.screenshot({
            path: path.join(screenshotsDir, '06-tablet-view.png'),
            fullPage: true
        });

        // Back to desktop and take final screenshot
        await page.setViewportSize({ width: 1280, height: 720 });
        await page.waitForTimeout(1000);

        // Screenshot 7: Final desktop overview
        console.log('📸 Taking final screenshot: Desktop overview');
        await page.screenshot({
            path: path.join(screenshotsDir, '07-final-desktop-overview.png'),
            fullPage: true
        });

        // Collect UI/UX analysis data
        console.log('🔍 Performing UI/UX analysis...');
        const analysis = await page.evaluate(() => {
            const results = {
                pageTitle: document.title,
                hasQRCode: !!document.querySelector('#qrcode canvas'),
                hasInstructions: !!document.querySelector('.instructions'),
                hasServerStatus: !!document.querySelector('.server-info'),
                hasRefreshButton: !!document.querySelector('#refreshBtn'),
                hasTimer: !!document.querySelector('#timer'),
                colorScheme: getComputedStyle(document.body).background,
                fontFamily: getComputedStyle(document.body).fontFamily,
                isResponsive: window.getComputedStyle(document.querySelector('.container')).maxWidth !== 'none',
                hasHeadings: document.querySelectorAll('h1, h2, h3, h4, h5, h6').length > 0,
                buttonCount: document.querySelectorAll('button').length
            };
            return results;
        });

        console.log('✅ Testing completed successfully!');
        return analysis;

    } catch (error) {
        console.error('❌ Test failed:', error);
        // Take error screenshot
        await page.screenshot({
            path: path.join(screenshotsDir, 'error-screenshot-simple.png'),
            fullPage: true
        });
        throw error;
    } finally {
        await browser.close();
        console.log('🏁 Browser closed');
    }
}

// Run the test
testClaudeCodeMonitorUI()
    .then((analysis) => {
        console.log('\n🎉 UI Testing Complete!');
        console.log('\n📁 Screenshots saved in:', screenshotsDir);
        console.log('\n📊 UI Analysis Summary:');
        console.log('- Page Title:', analysis.pageTitle);
        console.log('- QR Code Present:', analysis.hasQRCode ? '✅' : '❌');
        console.log('- Instructions Present:', analysis.hasInstructions ? '✅' : '❌');
        console.log('- Server Status Present:', analysis.hasServerStatus ? '✅' : '❌');
        console.log('- Refresh Button Present:', analysis.hasRefreshButton ? '✅' : '❌');
        console.log('- Timer Present:', analysis.hasTimer ? '✅' : '❌');
        console.log('- Responsive Design:', analysis.isResponsive ? '✅' : '❌');
        console.log('- Interactive Elements:', analysis.buttonCount, 'buttons');
        console.log('- Proper Headings:', analysis.hasHeadings ? '✅' : '❌');
        process.exit(0);
    })
    .catch((error) => {
        console.error('❌ Test execution failed:', error.message);
        process.exit(1);
    });