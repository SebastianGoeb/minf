#!/bin/bash

# cd to cript directory
dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd ${dir}

# Setup/clean controller
if [[ ! -d floodlight/.git ]]; then
    echo "**** SETTING UP CONTROLLER ****"
    echo
    rm -rf floodlight
    git clone git@github.com:floodlight/floodlight.git floodlight
    cd ${dir}/floodlight
    git submodule init
    git submodule update
else
    echo "**** CLEANING CONTROLLER ****"
    echo
    cd ${dir}/floodlight
    git reset --hard
    git clean -fd
fi

# Build controller
echo
echo "**** BUILDING CONTROLLER ****"
echo
cp -r ${dir}/controller/* .
ant

# Build server
echo
echo "**** BUILDING SERVER ****"
echo
cd ${dir}/server
mvn clean install
