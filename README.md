log-malloc-simple
=================

*log-malloc-simple* is **pre-loadable** library tracking all  memory allocations of a program. It produces simple text trace output, that makes it easy to find leaks and also identify their origin.

The output of *log-malloc-simple* can be easily parsed by a log analyzer tool. A simple log-analyzer written in Java is also part of this package.

#Changes compared to log-malloc2

log-malloc-simple is a much simplified version of log-malloc2. The simplifications and their reason:

- The return value of all allocations is returned intact. This prevents some crashes that were caused by log-malloc2 due to lack of memory alignment returned by modified calloc method.
- Only malloc_usable_size() is used to track the size of allocated memory.
- Conditional compilation is removed. I have no chance to test all versions of the code. Only platforms with GNU backtrace and malloc_usable_size are supported.
- Data aggregation of the logger is removed. It is delegated to a separate log-analyzer tool.

#Features

- logging to file descriptor 1022 (if opened)
- call stack **backtrace** (using GNU backtrace())
- thread safe

#Dependencies

- malloc_usable_size() method - could be get rid of but we could not track the size of freed memory chunks. In case we analyze all the logs of the whole lifecycle of the program then it could be accepted.
- pthread - for synchronizing logs from different threads.
- /proc/self/exe, /proc/self/cwd

#Usage

`LD_PRELOAD=./liblog-malloc-simple.so command args ... 1022>/tmp/program.log`

#Log analyzer tool

Standalone program written in Java. Usage:

- Create a pipe that will transfer memory allocation log to the Java program: `$ mkfifo /tmp/malloc.pipe`
- Start analyzer: `$ java -jar analyzer.jar /tmp/malloc.pipe`
- Use console to command analyzer: stop/start collecting data, print or save current state to file
- Start program to analyze: `$ LD_PRELOAD=./liblog-malloc-simple.so my_executable args ... 1022>/tmp/malloc.pipe`
- Run test cases that should run without leaking memory.
- See the output of the analyzer for non-freed memory chunks.

## Building instructions



# Author

- ***Andrew Schmidt***
 - **contact**: [https://github.com/rizsi](Github page of author)

# Licensing

* [LGPLv3](https://www.gnu.org/licenses/lgpl.html) for C code (library)
* [GPLv3](https://www.gnu.org/licenses/gpl.html) for Java malloc log analyzer

