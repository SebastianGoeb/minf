#!/bin/bash

dir=$(cd "$(dirname "$0")"; pwd)

build_controller()
{
    echo -e "\033[1m**** Building Controller ****\033[0m"
    cd "$dir/floodlight"
    ant
    echo
}

build_driver()
{
    echo -e "\033[1m**** Building Driver ****\033[0m"
    cd "$dir/driver"
    mvn clean install
    echo
}

build_server()
{
    echo -e "\033[1m**** Building Server ****\033[0m"
    cd "$dir/server"
    mvn clean install
    echo
}

while getopts "cds" opt; do
     case "$opt" in
         c)  build_controller
             ;;
         d)  build_driver
             ;;
         s)  build_server
             ;;
     esac
 done
