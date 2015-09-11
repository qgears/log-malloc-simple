#!/bin/bash

# Sends the output to another computer. This script is to be used when  memory
# leak analysis if the analyzed system has not enough processing power. The 
# output of the log-malloc-simple.so may be transferred to another computer, 
# on which is the 'receiver.sh' script is runnign.
#
# Prerequisite:
#
# mkfifo /tmp/malloc.pipe
#
# Note that creation of the output pipe must be performed before starting the
# software examined AND the software instrumented with log-malloc-simple.so, 
# if started from a script, might be blocking and will not start until this 
# script is run.

set +u

TARGET_HOST=$1
TARGET_PORT=${2-23456}

tail -F /tmp/malloc.pipe | netcat $TARGET_HOST $TARGET_PORT
