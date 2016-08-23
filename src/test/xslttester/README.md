# Test XSLT on inputs and expected outputs

./runtests.sh runs the tests

This depends on

* a saxon v9 JAR
* xmllint
* diff

...all of which should be installed on the machine prior to running the script.
You probably have xmllint and diff. 

All the tests should be in the tests directory,
with each one of the form:

testname.input.xml
testname.expectedoutput.xml


The bash script just loops through, runs the XSL on each input, then
compares the received result to the expected result. (via diff)

Example:

```
  ./runtests.sh -S ~/dev/java/lib/saxon9he.jar 
```



