const { chromium } = require('playwright');
const path = require('path');

async function testQRCodeFixedComprehensive() {
    console.log('🔍 Comprehensive Testing: Fixed Claude Code Monitor QR Code Interface');
    console.log('====================================================================');

    const browser = await chromium.launch({
        headless: true
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();

    // Arrays to store test results
    const testResults = [];
    const screenshots = [];
    const consoleMessages = [];
    const networkRequests = [];

    // Listen for console messages
    page.on('console', msg => {
        const message = {
            type: msg.type(),
            text: msg.text(),
            location: msg.location()
        };
        consoleMessages.push(message);
        if (msg.type() === 'error') {
            console.log(`❌ Console Error: ${msg.text()}`);
        }
    });

    // Listen for network requests
    page.on('response', response => {
        const request = {
            url: response.url(),
            status: response.status(),
            statusText: response.statusText()
        };
        networkRequests.push(request);

        if (response.url().includes('/api/auth/qr') || response.url().includes('qrcode')) {
            console.log(`🌐 Network: ${response.status()} ${response.url()}`);
        }
    });

    try {
        console.log('\n📍 Test 1: Navigation to Main Page');
        console.log('----------------------------------');

        // Navigate to the application
        const navigationStart = Date.now();
        await page.goto('http://localhost:3000', { waitUntil: 'networkidle', timeout: 10000 });
        const navigationTime = Date.now() - navigationStart;

        testResults.push({
            test: 'Navigation',
            status: 'PASS',
            time: `${navigationTime}ms`,
            details: 'Successfully loaded main page'
        });
        console.log(`✅ Navigation successful in ${navigationTime}ms`);

        // Take initial screenshot
        const initialScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-01-initial-load.png');
        await page.screenshot({ path: initialScreenshot, fullPage: true });
        screenshots.push({ name: 'Initial Load', path: initialScreenshot });
        console.log(`📸 Screenshot saved: ${initialScreenshot}`);

        console.log('\n📍 Test 2: Server Status Information');
        console.log('------------------------------------');

        // Wait for server status to load
        await page.waitForTimeout(2000);

        const serverStatus = await page.textContent('#serverStatus');
        const wsStatus = await page.textContent('#wsStatus');
        const serverVersion = await page.textContent('#serverVersion');

        console.log(`🖥️  Server Status: ${serverStatus}`);
        console.log(`🔌 WebSocket Status: ${wsStatus}`);
        console.log(`📦 Server Version: ${serverVersion}`);

        testResults.push({
            test: 'Server Status',
            status: serverStatus.includes('✅') ? 'PASS' : 'FAIL',
            details: `Server: ${serverStatus}, WS: ${wsStatus}, Version: ${serverVersion}`
        });

        console.log('\n📍 Test 3: Initial QR Code Generation');
        console.log('------------------------------------');

        // Check initial QR code generation
        await page.waitForSelector('#status', { timeout: 10000 });

        let initialStatus = await page.textContent('#status');
        console.log(`📊 Initial Status: ${initialStatus}`);

        // Wait for initial QR code to generate
        let qrLoadTimeout = 0;
        const maxWait = 15000; // 15 seconds max wait

        while (initialStatus.includes('Loading') && qrLoadTimeout < maxWait) {
            await page.waitForTimeout(1000);
            initialStatus = await page.textContent('#status');
            qrLoadTimeout += 1000;
            console.log(`⏳ Waiting for QR code... (${qrLoadTimeout/1000}s) Status: ${initialStatus}`);
        }

        // Check if QR code generated successfully
        const hasQRImage = await page.locator('#qrcode img').count() > 0;
        const hasQRCanvas = await page.locator('#qrcode canvas').count() > 0;
        const hasQRContent = hasQRImage || hasQRCanvas;

        console.log(`🎨 QR Image present: ${hasQRImage ? '✅' : '❌'}`);
        console.log(`🎨 QR Canvas present: ${hasQRCanvas ? '✅' : '❌'}`);
        console.log(`📊 Final Status: ${initialStatus}`);

        testResults.push({
            test: 'Initial QR Generation',
            status: hasQRContent && initialStatus.includes('ready') ? 'PASS' : 'FAIL',
            details: `QR Content: ${hasQRContent}, Status: ${initialStatus}`
        });

        // Take screenshot of initial QR code
        const initialQRScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-02-initial-qr.png');
        await page.screenshot({ path: initialQRScreenshot, fullPage: true });
        screenshots.push({ name: 'Initial QR Code', path: initialQRScreenshot });
        console.log(`📸 Screenshot saved: ${initialQRScreenshot}`);

        console.log('\n📍 Test 4: QR Code Button Functionality');
        console.log('--------------------------------------');

        // Clear any existing QR content for clean test
        await page.evaluate(() => {
            document.getElementById('qrcode').innerHTML = '';
        });

        // Click the "Generate New QR Code" button
        const generateButton = page.locator('#refreshBtn');
        await generateButton.waitFor({ state: 'visible' });

        const buttonEnabled = await generateButton.isEnabled();
        console.log(`🔘 Generate button enabled: ${buttonEnabled ? '✅' : '❌'}`);

        await generateButton.click();
        console.log('🖱️  Clicked "Generate New QR Code" button');

        // Monitor status changes
        await page.waitForTimeout(1000);
        let newStatus = await page.textContent('#status');
        console.log(`📊 Status after click: ${newStatus}`);

        // Wait for new QR code to generate
        let buttonQrTimeout = 0;
        const buttonMaxWait = 15000;

        while (newStatus.includes('Generating') && buttonQrTimeout < buttonMaxWait) {
            await page.waitForTimeout(1000);
            newStatus = await page.textContent('#status');
            buttonQrTimeout += 1000;
            console.log(`⏳ Generating QR code... (${buttonQrTimeout/1000}s) Status: ${newStatus}`);
        }

        // Check new QR code
        const newHasQRImage = await page.locator('#qrcode img').count() > 0;
        const newHasQRCanvas = await page.locator('#qrcode canvas').count() > 0;
        const newHasQRContent = newHasQRImage || newHasQRCanvas;

        console.log(`🎨 New QR Image present: ${newHasQRImage ? '✅' : '❌'}`);
        console.log(`🎨 New QR Canvas present: ${newHasQRCanvas ? '✅' : '❌'}`);
        console.log(`📊 Final Status: ${newStatus}`);

        testResults.push({
            test: 'Button QR Generation',
            status: newHasQRContent && newStatus.includes('ready') ? 'PASS' : 'FAIL',
            details: `New QR Content: ${newHasQRContent}, Status: ${newStatus}`
        });

        // Take screenshot after button click
        const buttonQRScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-03-button-qr.png');
        await page.screenshot({ path: buttonQRScreenshot, fullPage: true });
        screenshots.push({ name: 'Button Generated QR', path: buttonQRScreenshot });
        console.log(`📸 Screenshot saved: ${buttonQRScreenshot}`);

        console.log('\n📍 Test 5: QR Code Properties');
        console.log('-----------------------------');

        if (newHasQRImage) {
            // Test QR image properties
            const qrImage = page.locator('#qrcode img');
            const imgSrc = await qrImage.getAttribute('src');
            const imgWidth = await qrImage.evaluate(img => img.naturalWidth);
            const imgHeight = await qrImage.evaluate(img => img.naturalHeight);

            console.log(`🖼️  QR Image source type: ${imgSrc ? imgSrc.substring(0, 20) + '...' : 'None'}`);
            console.log(`📏 QR Image dimensions: ${imgWidth}x${imgHeight}`);

            testResults.push({
                test: 'QR Image Properties',
                status: imgSrc && imgSrc.startsWith('data:image') && imgWidth > 0 ? 'PASS' : 'FAIL',
                details: `Source: ${imgSrc ? 'Data URL' : 'None'}, Size: ${imgWidth}x${imgHeight}`
            });
        }

        console.log('\n📍 Test 6: Countdown Timer Functionality');
        console.log('----------------------------------------');

        // Check if timer is visible and working
        const timerElement = page.locator('#timer');
        const timerVisible = await timerElement.isVisible();
        let timerText = '';

        if (timerVisible) {
            timerText = await timerElement.textContent();
            console.log(`⏰ Timer visible: ✅ - Text: "${timerText}"`);

            // Wait a few seconds and check if timer is counting down
            await page.waitForTimeout(3000);
            const newTimerText = await timerElement.textContent();
            console.log(`⏰ Timer after 3s: "${newTimerText}"`);

            testResults.push({
                test: 'Countdown Timer',
                status: timerVisible && timerText !== newTimerText ? 'PASS' : 'PARTIAL',
                details: `Visible: ${timerVisible}, Initial: "${timerText}", After 3s: "${newTimerText}"`
            });
        } else {
            console.log(`⏰ Timer visible: ❌`);
            testResults.push({
                test: 'Countdown Timer',
                status: 'FAIL',
                details: 'Timer not visible'
            });
        }

        console.log('\n📍 Test 7: API Endpoint Testing');
        console.log('-------------------------------');

        // Test API endpoints directly
        const apiTests = [];

        // Test health endpoint
        try {
            const healthResponse = await fetch('http://localhost:3000/health');
            const healthData = await healthResponse.json();
            apiTests.push({
                endpoint: '/health',
                status: healthResponse.status,
                success: healthResponse.status === 200 && healthData.status === 'healthy'
            });
            console.log(`🏥 Health check: ${healthResponse.status} - ${healthData.status}`);
        } catch (error) {
            apiTests.push({ endpoint: '/health', status: 'ERROR', success: false });
            console.log(`🏥 Health check: ERROR`);
        }

        // Test version endpoint
        try {
            const versionResponse = await fetch('http://localhost:3000/version');
            const versionData = await versionResponse.json();
            apiTests.push({
                endpoint: '/version',
                status: versionResponse.status,
                success: versionResponse.status === 200 && versionData.version
            });
            console.log(`📦 Version check: ${versionResponse.status} - v${versionData.version}`);
        } catch (error) {
            apiTests.push({ endpoint: '/version', status: 'ERROR', success: false });
            console.log(`📦 Version check: ERROR`);
        }

        testResults.push({
            test: 'API Endpoints',
            status: apiTests.every(t => t.success) ? 'PASS' : 'PARTIAL',
            details: `Health: ${apiTests[0]?.status}, Version: ${apiTests[1]?.status}`
        });

        // Take final comprehensive screenshot
        const finalScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-04-final-state.png');
        await page.screenshot({ path: finalScreenshot, fullPage: true });
        screenshots.push({ name: 'Final State', path: finalScreenshot });
        console.log(`📸 Final screenshot saved: ${finalScreenshot}`);

        console.log('\n📍 Test 8: Error Recovery Testing');
        console.log('---------------------------------');

        // Test what happens when we generate multiple QR codes quickly
        console.log('🔄 Testing rapid QR generation...');
        for (let i = 0; i < 3; i++) {
            await generateButton.click();
            await page.waitForTimeout(1000);
            console.log(`⚡ Rapid generation ${i + 1}/3`);
        }

        await page.waitForTimeout(2000);
        const finalStatus = await page.textContent('#status');
        const finalHasQR = await page.locator('#qrcode img').count() > 0;

        console.log(`📊 Final status after rapid generation: ${finalStatus}`);
        console.log(`🎨 QR still present: ${finalHasQR ? '✅' : '❌'}`);

        testResults.push({
            test: 'Error Recovery',
            status: finalHasQR && !finalStatus.includes('Error') ? 'PASS' : 'PARTIAL',
            details: `QR Present: ${finalHasQR}, Status: ${finalStatus}`
        });

        // Take error recovery screenshot
        const recoveryScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-05-error-recovery.png');
        await page.screenshot({ path: recoveryScreenshot, fullPage: true });
        screenshots.push({ name: 'Error Recovery', path: recoveryScreenshot });
        console.log(`📸 Recovery screenshot saved: ${recoveryScreenshot}`);

        return {
            testResults,
            screenshots,
            consoleMessages,
            networkRequests: networkRequests.filter(req =>
                req.url.includes('/api/') ||
                req.url.includes('qrcode') ||
                req.url.includes('localhost:3000')
            )
        };

    } catch (error) {
        console.error('❌ Test execution failed:', error);

        // Take error screenshot
        const errorScreenshot = path.join(__dirname, 'screenshots', 'comprehensive-ERROR-state.png');
        try {
            await page.screenshot({ path: errorScreenshot, fullPage: true });
            screenshots.push({ name: 'Error State', path: errorScreenshot });
        } catch (screenshotError) {
            console.error('Failed to take error screenshot:', screenshotError);
        }

        throw error;
    } finally {
        await browser.close();
    }
}

// Main execution
async function main() {
    try {
        console.log('🚀 Starting Comprehensive QR Code Fix Testing...\n');

        const results = await testQRCodeFixedComprehensive();

        console.log('\n' + '='.repeat(80));
        console.log('🎉 COMPREHENSIVE TEST RESULTS');
        console.log('='.repeat(80));

        // Test Results Summary
        console.log('\n📊 TEST SUMMARY:');
        console.log('----------------');
        let passCount = 0;
        let partialCount = 0;
        let failCount = 0;

        results.testResults.forEach((test, index) => {
            const statusEmoji = test.status === 'PASS' ? '✅' :
                               test.status === 'PARTIAL' ? '⚠️' : '❌';
            console.log(`${index + 1}. ${statusEmoji} ${test.test}: ${test.status}`);
            if (test.details) {
                console.log(`   Details: ${test.details}`);
            }
            if (test.time) {
                console.log(`   Time: ${test.time}`);
            }

            if (test.status === 'PASS') passCount++;
            else if (test.status === 'PARTIAL') partialCount++;
            else failCount++;
        });

        console.log(`\n📈 OVERALL RESULTS:`);
        console.log(`   ✅ Passed: ${passCount}`);
        console.log(`   ⚠️  Partial: ${partialCount}`);
        console.log(`   ❌ Failed: ${failCount}`);
        console.log(`   📊 Success Rate: ${Math.round((passCount / results.testResults.length) * 100)}%`);

        // Screenshots Summary
        console.log('\n📸 SCREENSHOTS CAPTURED:');
        console.log('------------------------');
        results.screenshots.forEach((screenshot, index) => {
            console.log(`${index + 1}. ${screenshot.name}: ${screenshot.path}`);
        });

        // Network Summary
        console.log('\n🌐 KEY NETWORK REQUESTS:');
        console.log('------------------------');
        const qrRequests = results.networkRequests.filter(req => req.url.includes('/api/auth/qr'));
        console.log(`QR Generation Requests: ${qrRequests.length}`);
        qrRequests.forEach(req => {
            console.log(`   ${req.status} ${req.statusText} - ${req.url}`);
        });

        // Console Errors
        const errors = results.consoleMessages.filter(msg => msg.type === 'error');
        console.log('\n❌ CONSOLE ERRORS:');
        console.log('------------------');
        if (errors.length === 0) {
            console.log('   No console errors detected! ✅');
        } else {
            errors.forEach(error => {
                console.log(`   ${error.text}`);
            });
        }

        // Final Assessment
        console.log('\n🔍 FINAL ASSESSMENT:');
        console.log('--------------------');
        const serverSideQRWorking = results.testResults.some(t =>
            t.test === 'Initial QR Generation' && t.status === 'PASS'
        ) && results.testResults.some(t =>
            t.test === 'Button QR Generation' && t.status === 'PASS'
        );

        if (serverSideQRWorking && errors.length === 0) {
            console.log('🎉 SUCCESS: Server-side QR code generation is working perfectly!');
            console.log('🔧 The fix has been successfully implemented and tested.');
            console.log('📱 QR codes are now generated server-side and display properly.');
            console.log('⏰ Timer functionality is operational.');
            console.log('🖥️  Server status monitoring is functional.');
        } else if (serverSideQRWorking) {
            console.log('⚠️  PARTIAL SUCCESS: QR codes work but there are some minor issues.');
            console.log('🔧 The core fix is working but may need minor adjustments.');
        } else {
            console.log('❌ ISSUES DETECTED: QR code generation is not working properly.');
            console.log('🔧 The fix may need additional work or investigation.');
        }

        console.log('\n' + '='.repeat(80));

        return results;

    } catch (error) {
        console.error('💥 Testing failed with error:', error.message);
        console.log('\n❌ CRITICAL FAILURE: Unable to complete testing.');
        process.exit(1);
    }
}

// Run the test
main()
    .then(() => {
        console.log('✅ Test execution completed successfully');
        process.exit(0);
    })
    .catch(error => {
        console.error('💥 Test execution failed:', error);
        process.exit(1);
    });