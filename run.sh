#!/bin/bash

#java -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.SimpleLog -Dorg.apache.commons.logging.simplelog.log.org.apache.http.wire=ERROR -Dorg.apache.commons.logging.simplelog.log.org.apache.http=DEBUG -jar target/filereader-1.0.0-SNAPSHOT-jar-with-dependencies.jar $@
java -jar target/filereader-1.0.0-SNAPSHOT-jar-with-dependencies.jar $@