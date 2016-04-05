(
echo "var define = function(cb) { var res = cb(); }; define.amd = {};\n"
cat /Users/chugai/dev/rxjs-bridge/src/main/resources/rx/rx.all.min.js
echo ";"
cat target/classes/stjs.js
echo ";"
echo "stjs.global = GLOBAL;"
cat /Users/chugai/dev/jslang/target/classes/polyfills.js
echo ";"
cat /Users/chugai/dev/jslang/target/classes/jslang.js
echo ";"
cat /Users/chugai/dev/jcodec/target/classes/jcodec.js
echo ";"
cat target/classes/hlsdash.js
echo ";"
cat target/generated-test-js/hlsdash.js
echo ";"
echo 'new TsWorkerTest().testPipeline();'
)> test.js

node test.js
