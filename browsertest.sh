. libpaths

(
echo "var define = function(cb) { var res = cb(); }; define.amd = {};\n"
echo "require = function() { return null; };"
cat $RX/src/main/resources/rx/rx.all.min.js
echo ";"
cat target/classes/stjs.js
echo ";"
cat $JSLANG/target/classes/polyfills.js
echo ";"
cat $JSLANG/target/classes/jslang.js
echo ";"
cat $JCODEC/target/classes/jcodec.js
echo ";"
cat target/classes/hlsdash.js
echo ";"
cat target/generated-test-js/hlsdash.js
echo ";"
)> browser.js

echo ok
