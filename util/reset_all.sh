#!/bin/sh

set -x
set -e

BASE=$PWD
DIRS="sdkd-java couchbase-java-client spymemcached"
for dir in $DIRS; do
    cd $BASE/$dir

    if [ ! -e .git ]; then
        echo "Not cleaning non-git repo $dir"
        continue
    fi

    git clean -dfx
    git reset --hard
done

rm -f $BASE/sdkd_exec.jar
rm -rf $BASE/stash
