#!/bin/bash
#Start with setting the variables

BUILD_STASH=$PWD/stash
BUILD_ROOT=$(pwd)

if [ ! -d "$BUILD_STASH" ]; then
    mkdir $BUILD_STASH
fi

cd $BUILD_ROOT/couchbase-java-client
CB_VERSION=$(git describe)

# Don't pollute user/global repo
export ANT_OPTS="$ANT_OPTS -Duser.home=$BUILD_STASH"

# Setup our commands
ANTCMD=ant
MVNCMD="mvn -q -Duser.home=$BUILD_STASH"
GRADLECMD="./gradlew -Duser.home=$BUILD_STASH"

cd $BUILD_ROOT/java-couchbase-encryption
$MVNCMD clean
$MVNCMD versions:set -DnewVersion=999.999.999-SNAPSHOT
$MVNCMD -DskipTests=true install
rc=$?
if [[ $rc -ne 0 ]] ; then
     echo 'could not build core'; exit $rc
fi


cd $BUILD_ROOT/couchbase-jvm-core
#$GRADLECMD clean install publishToMavenLocal
$MVNCMD clean
$MVNCMD versions:set -DnewVersion=999.999.999-SNAPSHOT
$MVNCMD -DskipTests=true install
rc=$?
if [[ $rc -ne 0 ]] ; then
     echo 'could not build core'; exit $rc
fi

cd $BUILD_ROOT/couchbase-java-client
#$GRADLECMD clean install publishToMavenLocal
$MVNCMD clean
$MVNCMD versions:update-child-modules
$MVNCMD versions:set -DnewVersion=999.999.999-SNAPSHOT
$MVNCMD -Dcore.version=999.999.999-SNAPSHOT -Dencryptionextension.version=999.999.999-SNAPSHOT -DskipTests=true install
echo "$MVNCMD versions:set -DnewVersion=999.999.999-SNAPSHOT -Dcore.version=999.999.999-SNAPSHOT -Dencryptionextension.version=999.999.999-SNAPSHOT -DskipTests=true install"
rc=$?
if [[ $rc -ne 0 ]] ; then
     echo 'could not build client'; exit $rc
fi
#Compile the sdkd source code using the above dependencies
cd $BUILD_ROOT/sdkd-java
$MVNCMD versions:update-child-modules
$MVNCMD clean package

#$MVNCMD clean package
#finally create a single exec jar using the target create_run_jar
#cd $BUILD_ROOT
#echo $(pwd)
#rm sdkd_exec.jar
#$ANTCMD create_run_jar
