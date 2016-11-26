#!/bin/bash

# Script directory
dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $dir

# Setup/clean Floodlight
if [[ ! -d floodlight/.git ]]; then
    echo "**** SETTING UP (this could take a while) ****"
    echo
    rm -rf floodlight
    git clone git@github.com:floodlight/floodlight.git floodlight
    cd $dir/floodlight
    git submodule init
    git submodule update
else
    echo "**** CLEANING ****"
    echo
    cd $dir/floodlight
    git reset --hard
    git clean -fd
fi

# Build
echo
echo "**** BUILDING ****"
echo
cp -r $dir/controller/controller/* .
ant
