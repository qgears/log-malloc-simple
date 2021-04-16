SHELL := /bin/bash

.PHONY: run-hellow

all: log-malloc-simple.so



log-malloc-simple.so: log-malloc-simple.c
	gcc -Wno-unused-result -o log-malloc-simple.so -shared -fPIC -DPIC log-malloc-simple.c -lpthread -ldl -nostartfiles -O2

hellow: hellow.c
	gcc -o hellow -rdynamic hellow.c

# Example that must be executed with a java analyzer already existing
run-hellow-tcp: hellow log-malloc-simple.so
	LD_PRELOAD=./log-malloc-simple.so ./hellow 1022>/dev/tcp/localhost/9999

# Example that puts all output to stdout just to show how it looks
run-hellow: hellow log-malloc-simple.so
	LD_PRELOAD=./log-malloc-simple.so ./hellow 1022>&1

clean:
	rm -f log-malloc-simple.so

