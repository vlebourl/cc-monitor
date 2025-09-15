const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

// Create screenshots directory
const screenshotsDir = path.join(__dirname, 'screenshots');
if (!fs.existsSync(screenshotsDir)) {
    fs.mkdirSync(screenshotsDir);
}

async function testAPIEndpoints() {
    console.log('🔗 Testing API endpoints...');

    const browser = await chromium.launch({
        headless: true,
        slowMo: 300
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();

    try {
        const endpoints = [
            { name: 'health', url: '/health', screenshot: '08-health-endpoint.png' },
            { name: 'version', url: '/version', screenshot: '09-version-endpoint.png' },
            { name: 'api-info', url: '/api', screenshot: '10-api-info.png' },
            { name: 'status', url: '/api/status', screenshot: '11-status-endpoint.png' },
            { name: 'websocket', url: '/api/websocket', screenshot: '12-websocket-endpoint.png' }
        ];

        for (const endpoint of endpoints) {
            console.log(`📸 Testing ${endpoint.name} endpoint: ${endpoint.url}`);
            await page.goto(`http://localhost:3000${endpoint.url}`, { waitUntil: 'networkidle' });
            await page.waitForTimeout(1000);

            await page.screenshot({
                path: path.join(screenshotsDir, endpoint.screenshot),
                fullPage: true
            });

            console.log(`✅ ${endpoint.name} endpoint screenshot saved`);
        }

        console.log('🎉 All API endpoint testing completed successfully!');

    } catch (error) {
        console.error('❌ API endpoint test failed:', error);
        await page.screenshot({
            path: path.join(screenshotsDir, 'api-error-screenshot.png'),
            fullPage: true
        });
        throw error;
    } finally {
        await browser.close();
        console.log('🏁 Browser closed');
    }
}

// Run the API endpoint test
testAPIEndpoints()
    .then(() => {
        console.log('\n🎉 API Endpoint Testing Complete!');
        process.exit(0);
    })
    .catch((error) => {
        console.error('❌ API endpoint test execution failed:', error.message);
        process.exit(1);
    });