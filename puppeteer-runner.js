const puppeteer = require('puppeteer');

(async() => {
    const browser = await puppeteer.launch({headless: false,
                                            defaultViewport: {width: 800,
                                                              height: 2400}});
    const page = await browser.newPage();
    page.on('console', msg => console.log(msg.text()));
    await page.goto('http://localhost:3449/test.html');
    const result = await page.evaluate(async function() {
        function sleep(ms) {
            return new Promise(resolve => setTimeout(resolve, ms));
        }

        async function loop_until_predicate_true(predicate) {
            for (let i = 0; i < 200; i++) {
                if(predicate()) { break; }
                await sleep(100);
            }
        }

        firemore.test_runner.run();
        await loop_until_predicate_true(firemore.test_runner.is_complete);
    });
    await page.screenshot({path: 'example.png'});
    await browser.close();
})();
