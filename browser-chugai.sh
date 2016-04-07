(
echo "var define = function(cb) { var res = cb(); }; define.amd = {};\n"
echo "require = function() { return null; };"
cat /Users/chugai/dev/rxjs-bridge/src/main/resources/rx/rx.all.min.js
echo ";"
cat target/classes/stjs.js
echo ";"
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
)> hlsdash.js
