#!/bin/bash

cd "$(dirname $0)"
source ./env.sh

mvn install

#pushd attach-wireguard
#./gradlew publishToMavenLocal
#popd

