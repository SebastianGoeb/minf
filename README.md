# MInf Project (Part 2)

## Setup
Use `./setup` to setup the floodlight environment. This pulls a fresh copy of the floodlight repo and symlinks our files into the resulting directory.

## Building
Use `./build -cds` to build the controller, driver and server. This requires floodlight to be set up with the above script.

## Controller
A floodlight module that performs proactive load balancing.

## Driver
This driver script generates http requests from arbitrary IP addresses for arbitrary amounts of data (see server) and with arbitrary rate limits. Run with `sudo java -jar driver/target/driver.jar experiments/test.json`. Sudo is necessary since the application will assign and unassign IP addresses to a given interface. The flags `-d` and `-v` toggle dry-run mode and verbose mode respectively.

## Server
This server generates HTTP responses of arbitrary length at `http://<serverip>:8080/<amount>`. It accepts a human readable length with any of these units: `1234 | 1k | 2K | 3M | 4G | 5T | 6P`. Only integers are allowed and unitless numbers are intepreted as bytes. Run with `java -jar server/target/server.jar`. If running in the background, make sure to redirect output to /dev/null as failure to do so may cause stdout to fill up and the server to stop functioning.

## Dependencies
* python 2
* maven
* ant
* git

## Notes
All relevant code for the project's controller, driver, and server applications is contained within the respective directories (controller/, driver/, server/). The experiments/ directory contains a sample json file to feed to the driver application. The \*-config/ directories contain various config scripts to setup the testbed switches and servers, though this is largely irrelevant since the application should be run on Mininet anyway.

To run the system, make sure the driver has arp and routing table entries for the load balancer's VIP (10.5.1.12 by default)
```
ip route add 10.0.0.0/8 dev h1-eth0
arp -s 10.5.1.12 00:00:0a:05:01:0c
```

Also make sure the servers have a gateway setup for the 10.0.0.0/8 subnet that points to the load balancer's VIP. The servers should also have arp entries
```
ip route del 10.0.0.0/8 dev h2-eth0
ip route add 10.5.1.12 dev h2-eth0
ip route add 10.0.0.0/8 via 10.5.1.12 dev h2-eth0
arp -s 10.5.1.12 00:00:0a:05:01:0c
```
