#!/bin/sh

set -e

BUILD_DIR=$PWD

if [ -d $BUILD_DIR/sdkd-java/ ]; then
    echo "Can't overwrite existing dir"
    exit 1
fi

mkdir $BUILD_DIR/sdkd-java

cd ../
git archive HEAD | tar -C $BUILD_DIR/sdkd-java -x
