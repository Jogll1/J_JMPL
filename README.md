# Java interpreter for the JMPL language

Implementation of the Java tree-walk interpreter from Crafting Interpreters by Robert Nystrom.

## Running the Interpreter
Currently, to run the interpreter cd to root of the repo and run the commands:\
`javac -d ./bin ./src/com/jmpl/j_jmpl/*.java` to compile to `bin` then,\
`java -cp ./bin com.jmpl.j_jmpl.JMPL <path/to/file>` to run the interpreter on a source file.

Running the interpreter with no source file will start the in-terminal REPL.
