const puppeteer = require('puppeteer');

(async () => {
    const browser = await puppeteer.launch({headless: true,
                                            defaultViewport: {width: 800,
                                                              height: 2400}});
    const page = await browser.newPage();

    page.on('console', msg => console.log(msg.text()));

    await page.goto('http://localhost:3449/test-without-ui.html');

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

        await loop_until_predicate_true(firemore.test_runner.is_complete);

        return firemore.test_runner.is_successful();
    });

    var is_successful = await result;

    await browser.close();

    if(!is_successful){
        console.log("FAAAILLIUUURE");
        process.exit(1);
    }
    // await page.screenshot({path: 'puppeteer_result.png'});
})();


