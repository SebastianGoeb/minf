# MInf Project (Part 2)

## Controller
A floodlight module that performs proactive load balancing.

### Import Into Eclipse

```
./master/build.sh
cd floodlight
ant eclipse
```

In Eclipse:
1. Import Floodlight
  * Open eclipse and create a new workspace
  * File -> Import -> General -> Existing Projects into Workspace. Then click "Next".
  * From "Select root directory" click "Browse". Select the parent directory where you placed floodlight earlier.
  * Check the box for "Floodlight". No other Projects should be present and none should be selected.
  * Click Finish.
2. Create run configuration
  * Click Run->Run Configurations
  * Right Click Java Application->New
  * For Name use FloodlightLaunch
  * For Project use Floodlight
  * For Main use net.floodlightcontroller.core.Main
  * Click Apply
3. Link load balancer files within Eclipse (in project explorer tab do:)
  * Right Click src/main/java/net/floodlightcontroller -> New -> Folder -> Advanced -> Link to alternate location -> <controller dir>/src/main/java/net/floodlight/controller/proactiveloadbalancer -> Finish
  * Delete src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule
  * Right Click src/main/resources/META-INF/services -> New -> File -> Advanced -> Link to file in the filesystem -> <controller dir>/src/main/resources/META-INF/services/net.floodlightcontroller.core.module.IFloodlightModule -> Finish
  * Delete src/main/resources/floodlightdefault.properties
  * Right Click src/main/resources -> New -> File -> Advanced -> Link to file in the filesystem -> <controller dir>/src/main/resources/floodlightdefault.properties -> Finish

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
TODO

## Dependencies
* python 2
* maven
* ant
* git
