#!/bin/bash
THE_CLASSPATH=:./jars/stormcrawler-0.1.demotest.jar
#java 1.8 
export JAVA_HOME=$(/usr/libexec/java_home)
#export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_212.jdk/Contents/Home

$JAVA_HOME/bin/java  -classpath $THE_CLASSPATH  gov.lanl.crawler.boundary.TraceTest $1 $2

echo 
