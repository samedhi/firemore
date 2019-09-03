var page = require('webpage').create();
var system = require('system');

if (system.args.length !== 2) {
    console.log('Expected a target URL parameter.');
    phantom.exit(1);
}

page.onConsoleMessage = function (message) {
    console.log(message);
};

var url = system.args[1];

page.open(url, function (status) {

    if (status !== "success") {
        console.log('Failed to open ' + url);
        setTimeout(function() { // https://github.com/ariya/phantomjs/issues/12697
            phantom.exit(1);
        }, 0);
    }

    page.evaluate(function() {
        try {
            firemore.test_runner.run()
        } catch(error){
            console.log(error);
        }
        if(firemore.test_runner.exit_code != 0) {
            // phantom.exit(global_exit_code);
        }
    });

    setTimeout(function() { // https://github.com/ariya/phantomjs/issues/12697
        phantom.exit(0);
    }, 0);
});
