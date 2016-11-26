# MInf Project (Part 2)

## Controller
A floodlight module that performs proactive load balancing.

## Driver
This driver script generates http requests from arbitrary IP addresses for arbitrary amounts of data (see server) and with arbitrary rate limits.

It can be controlled via an http server on `http://<driver ip>:8080`. Start an experiment by POST-ing a JSON spec and interrupt a running experiment by DELETE-ing it. The master.py script does this automatically.

## Server
This server generates HTTP responses of arbitrary length at `http://<serverip>:8080/<length>`. It accepts a human readable length with any of these units: `1234 | 1k | 2K | 3M | 4G | 5T | 6P`. Only integers are allowed and unitless numbers are intepreted as bytes.

## Build
`./build.sh` to build the controller and server. The driver does not require building.

## Deploy
`./deploy.py -t` for testbed deploy.

`./deploy.py -m` for mininet deploy.

Modify `topology.py` to deploy to a different setup.

## Run
`./master.py` to run an experiment.