#!/bin/bash

dir=$(cd "$(dirname "$0")"; pwd)

# Build controller
build_controller()
{
    echo "**** BUILDING CONTROLLER ****"
    cd "$dir/floodlight"
    ant
}

# Build server
build_server()
{
    echo "**** BUILDING SERVER ****"
    cd "$dir/server"
    mvn clean install
}

echo "unfinished script!"
