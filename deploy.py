#!/usr/bin/env python

from __future__ import print_function
import subprocess
import sys
import os


def clean(topo):
    # Clean
    procs = []
    hosts = topo.controllers + topo.drivers + topo.servers
    for name, _, _ in hosts:
        proc = subprocess.Popen(['ssh', '-oBatchMode=yes', name, 'rm -rf deploy && mkdir deploy'],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        procs.append((name, proc))

    # Wait for procs to finish
    print('Clean')
    print('Progress: ', end='')
    sys.stdout.flush()
    failed = []
    for name, proc in procs:
        proc.wait()
        if proc.returncode:
            failed.append(name)
        print('x' if proc.returncode else '.', end='')
        sys.stdout.flush()
    print()

    # Report failed procs
    if failed:
        print('Failed: ' + ' '.join(failed))
    else:
        print('Success\n')

    return not failed


def deploy(topo):
    procs = []
    # Controllers
    for name, _, _ in topo.controllers:
        local_path = 'floodlight/target/floodlight.jar'
        remote_path = name + ':deploy/controller.jar'
        proc = subprocess.Popen(['scp', local_path, remote_path])
        procs.append((name, proc))
    # Drivers
    for name, _, _ in topo.drivers:
        local_path = 'driver/driver.py'
        remote_path = name + ':deploy/driver.py'
        proc = subprocess.Popen(['scp', local_path, remote_path],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        procs.append((name, proc))
    # Servers
    for name, _, _ in topo.servers:
        local_path = 'server/target/server-jar-with-dependencies.jar'
        remote_path = name + ':deploy/server.jar'
        proc = subprocess.Popen(['scp', local_path, remote_path],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        procs.append((name, proc))

    # Wait for procs to finish
    print('Deploy')
    print('Progress: ', end='')
    sys.stdout.flush()
    failed = []
    for name, proc in procs:
        proc.wait()
        if proc.returncode:
            failed.append(name)
        print('x' if proc.returncode else '.', end='')
        sys.stdout.flush()
    print()

    # Report failed procs
    if failed:
        print('Failed: ' + ' '.join(failed))
    else:
        print('Success\n')

    return not failed


def run():
    import argparse
    import topology

    # Parse args
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument('-t', '--testbed', action='store_true', help='install to testbed')
    group.add_argument('-m', '--mininet', action='store_true', help='install to mininet')
    args = parser.parse_args()

    topo = topology.mininet if args.mininet else topology.testbed

    clean(topo)
    deploy(topo)


if __name__ == '__main__':
    run()
