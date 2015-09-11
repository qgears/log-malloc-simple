#!/bin/bash

# Pair of sender.shs

set +u

# Listen port: 1st command line argument; 23456 by default
LISTEN_PORT=${1-23456}
OUTPUT_PIPE=/tmp/malloc.pipe

mkfifo $OUTPUT_PIPE

# Interactive, text-interfaced java analyzer will get the standard input 
(java -jar ../target/analyzer.jar $OUTPUT_PIPE <&1) &

netcat -l $LISTEN_PORT > $OUTPUT_PIPE
