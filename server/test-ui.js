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
        headless: true, // Running headless since no X server available
        slowMo: 500 // Slightly faster in headless mode
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();

    try {
        // Navigate to the application
        console.log('📱 Navigating to http://localhost:3000...');
        await page.goto('http://localhost:3000');

        // Wait for the page to load
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(2000);

        // Screenshot 1: Main page initial load
        console.log('📸 Taking screenshot: Main page initial load');
        await page.screenshot({
            path: path.join(screenshotsDir, '01-main-page-initial.png'),
            fullPage: true
        });

        // Wait for QR code to load
        console.log('⏳ Waiting for QR code generation...');

        // Check if page has loaded properly
        const pageContent = await page.content();
        console.log('📄 Page loaded, checking for QR code generation...');

        // Wait for either canvas or error message
        try {
            await page.waitForSelector('#qrcode canvas, .status.error', { timeout: 15000 });
            console.log('✅ QR code element found or error displayed');
        } catch (error) {
            // Take debug screenshot
            await page.screenshot({
                path: path.join(screenshotsDir, 'debug-qr-timeout.png'),
                fullPage: true
            });

            // Check what's actually on the page
            const statusText = await page.textContent('#status').catch(() => 'Status element not found');
            console.log('🔍 Status text:', statusText);
            throw new Error(`QR code did not load. Status: ${statusText}`);
        }

        await page.waitForTimeout(2000);

        // Screenshot 2: Main page with QR code
        console.log('📸 Taking screenshot: Main page with QR code');
        await page.screenshot({
            path: path.join(screenshotsDir, '02-main-page-with-qr.png'),
            fullPage: true
        });

        // Test QR code regeneration
        console.log('🔄 Testing QR code regeneration...');
        const refreshButton = page.locator('#refreshBtn');
        await refreshButton.click();

        // Wait for new QR code
        await page.waitForTimeout(3000);

        // Screenshot 3: QR code regeneration process
        console.log('📸 Taking screenshot: QR code regeneration');
        await page.screenshot({
            path: path.join(screenshotsDir, '03-qr-regeneration.png'),
            fullPage: true
        });

        // Test server status information
        console.log('📊 Checking server status information...');

        // Wait for server status to load
        await page.waitForSelector('#serverStatus', { timeout: 5000 });

        // Screenshot 4: Server status info
        console.log('📸 Taking screenshot: Server status information');
        await page.screenshot({
            path: path.join(screenshotsDir, '04-server-status.png'),
            fullPage: true
        });

        // Test API endpoints
        console.log('🔗 Testing API endpoints...');

        // Navigate to health endpoint
        await page.goto('http://localhost:3000/health');
        await page.waitForLoadState('networkidle');

        // Screenshot 5: Health endpoint
        console.log('📸 Taking screenshot: Health endpoint');
        await page.screenshot({
            path: path.join(screenshotsDir, '05-health-endpoint.png'),
            fullPage: true
        });

        // Navigate to version endpoint
        await page.goto('http://localhost:3000/version');
        await page.waitForLoadState('networkidle');

        // Screenshot 6: Version endpoint
        console.log('📸 Taking screenshot: Version endpoint');
        await page.screenshot({
            path: path.join(screenshotsDir, '06-version-endpoint.png'),
            fullPage: true
        });

        // Navigate to API info endpoint
        await page.goto('http://localhost:3000/api');
        await page.waitForLoadState('networkidle');

        // Screenshot 7: API info endpoint
        console.log('📸 Taking screenshot: API info endpoint');
        await page.screenshot({
            path: path.join(screenshotsDir, '07-api-info.png'),
            fullPage: true
        });

        // Navigate to status endpoint
        await page.goto('http://localhost:3000/api/status');
        await page.waitForLoadState('networkidle');

        // Screenshot 8: Status endpoint
        console.log('📸 Taking screenshot: Status endpoint');
        await page.screenshot({
            path: path.join(screenshotsDir, '08-status-endpoint.png'),
            fullPage: true
        });

        // Go back to main page
        await page.goto('http://localhost:3000');
        await page.waitForLoadState('networkidle');
        await page.waitForSelector('#qrcode canvas', { timeout: 10000 });
        await page.waitForTimeout(2000);

        // Test responsive design - mobile view
        console.log('📱 Testing mobile responsive design...');
        await page.setViewportSize({ width: 375, height: 667 }); // iPhone SE size
        await page.waitForTimeout(1000);

        // Screenshot 9: Mobile view
        console.log('📸 Taking screenshot: Mobile view');
        await page.screenshot({
            path: path.join(screenshotsDir, '09-mobile-view.png'),
            fullPage: true
        });

        // Test tablet view
        console.log('📱 Testing tablet responsive design...');
        await page.setViewportSize({ width: 768, height: 1024 }); // iPad size
        await page.waitForTimeout(1000);

        // Screenshot 10: Tablet view
        console.log('📸 Taking screenshot: Tablet view');
        await page.screenshot({
            path: path.join(screenshotsDir, '10-tablet-view.png'),
            fullPage: true
        });

        // Back to desktop view
        await page.setViewportSize({ width: 1280, height: 720 });
        await page.waitForTimeout(1000);

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
                isResponsive: window.getComputedStyle(document.querySelector('.container')).maxWidth !== 'none'
            };

            // Check accessibility
            const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
            results.hasHeadings = headings.length > 0;

            // Check interactive elements
            const buttons = document.querySelectorAll('button');
            results.buttonCount = buttons.length;

            return results;
        });

        // Final screenshot with desktop view
        console.log('📸 Taking final screenshot: Desktop overview');
        await page.screenshot({
            path: path.join(screenshotsDir, '11-final-desktop-overview.png'),
            fullPage: true
        });

        console.log('✅ Testing completed successfully!');
        console.log('📊 Analysis Results:', JSON.stringify(analysis, null, 2));

        return analysis;

    } catch (error) {
        console.error('❌ Test failed:', error);
        // Take error screenshot
        await page.screenshot({
            path: path.join(screenshotsDir, 'error-screenshot.png'),
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
    })
    .catch((error) => {
        console.error('❌ Test execution failed:', error.message);
        process.exit(1);
    });