#!/usr/bin/env bash
TOPDIR=$(cd `dirname $0`/..;pwd)
source $TOPDIR/bin/prepare-env.sh

# Get list of PIDs to which the application should attach
pids=$(jps -l | grep "com.distrace.examples.$1" | cut -d" " -f1)

for pid in $pids
do
 echo "Attaching agent to process $pid"
 java -cp "$AGENT_FILE" "com.distrace.DistraceAgent" $pid "$AGENT_JAR_FILE"
done