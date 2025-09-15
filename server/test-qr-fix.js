const { chromium } = require('playwright');
const path = require('path');

async function testQRCodeFix() {
    console.log('🔍 Testing QR Code functionality and potential fixes...');

    const browser = await chromium.launch({
        headless: true
    });

    const context = await browser.newContext({
        viewport: { width: 1280, height: 720 }
    });

    const page = await context.newPage();

    try {
        // Navigate to the application
        await page.goto('http://localhost:3000', { waitUntil: 'networkidle' });

        // Check if QRCode library loaded
        const qrCodeDefined = await page.evaluate(() => {
            return typeof window.QRCode !== 'undefined';
        });

        console.log('📦 QRCode library loaded:', qrCodeDefined ? '✅' : '❌');

        // Check network requests for CDN
        const responses = [];
        page.on('response', response => {
            if (response.url().includes('qrcode')) {
                responses.push({
                    url: response.url(),
                    status: response.status(),
                    statusText: response.statusText()
                });
            }
        });

        // Wait a bit for network requests
        await page.waitForTimeout(3000);

        console.log('🌐 QR Code CDN requests:', responses);

        // Check console errors
        const consoleMessages = [];
        page.on('console', msg => {
            if (msg.type() === 'error') {
                consoleMessages.push(msg.text());
            }
        });

        // Try to manually load QRCode if it failed
        if (!qrCodeDefined) {
            console.log('🔄 Attempting to manually load QRCode...');

            // Inject QRCode library directly
            await page.addScriptTag({
                url: 'https://cdn.jsdelivr.net/npm/qrcode@1.5.3/build/qrcode.min.js'
            });

            await page.waitForTimeout(2000);

            const qrCodeLoadedAfterFix = await page.evaluate(() => {
                return typeof window.QRCode !== 'undefined';
            });

            console.log('📦 QRCode library after manual load:', qrCodeLoadedAfterFix ? '✅' : '❌');

            if (qrCodeLoadedAfterFix) {
                // Try to generate QR code manually
                await page.evaluate(() => {
                    const qrContainer = document.getElementById('qrcode');
                    if (qrContainer && window.QRCode) {
                        window.QRCode.toCanvas(qrContainer, 'test-data', {
                            width: 280,
                            margin: 2
                        }).then(() => {
                            console.log('QR code generated successfully');
                        }).catch(err => {
                            console.error('QR generation failed:', err);
                        });
                    }
                });

                await page.waitForTimeout(2000);

                // Check if canvas was created
                const hasCanvas = await page.evaluate(() => {
                    return !!document.querySelector('#qrcode canvas');
                });

                console.log('🎨 QR Canvas created after fix:', hasCanvas ? '✅' : '❌');
            }
        }

        console.log('💬 Console errors:', consoleMessages);

        // Take screenshot of current state
        await page.screenshot({
            path: path.join(__dirname, 'screenshots', 'qr-debug-test.png'),
            fullPage: true
        });

        return {
            qrCodeDefined,
            responses,
            consoleMessages
        };

    } catch (error) {
        console.error('❌ QR Code test failed:', error);
        throw error;
    } finally {
        await browser.close();
    }
}

// Run the test
testQRCodeFix()
    .then((results) => {
        console.log('\n🎉 QR Code Debug Test Complete!');
        console.log('Results:', JSON.stringify(results, null, 2));
    })
    .catch((error) => {
        console.error('❌ QR Debug test failed:', error.message);
        process.exit(1);
    });