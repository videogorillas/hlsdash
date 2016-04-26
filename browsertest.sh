(
echo "var define = function(cb) { var res = cb(); }; define.amd = {};\n"
echo "require = function() { return null; };"
cat /Users/zhukov/git/rxjs-bridge/src/main/resources/rx/rx.all.min.js
echo ";"
cat target/classes/stjs.js
echo ";"
cat /Users/zhukov/workspaces/jcodecjs/jslang/target/classes/polyfills.js
echo ";"
cat /Users/zhukov/workspaces/jcodecjs/jslang/target/classes/jslang.js
echo ";"
cat /Users/zhukov/git/jcodec/target/classes/jcodec.js
echo ";"
cat target/classes/hlsdash.js
echo ";"
cat target/generated-test-js/hlsdash.js
echo ";"
)> hlsdash.js

