#!/usr/bin/python2

import subprocess
import urllib
import time


commands = {
    'server': {
        'start': 'java -jar ~/deploy/server.jar',
        'stop': 'wget -O - http://{ip}:{port}/stop'
    },
    'driver': {
        'start': 'python ~/deploy/driver.py',
        'stop': 'wget -O - http://{ip}:{port}/stop'
    },
    'controller': {
        'start': 'java -jar ~/deploy/controller.jar',
        'stop': 'kill -9 $(pgrep java)'
    }
}


def starthosts(hosts, command):
    print('starting servers')
    processes = [subprocess.Popen(['ssh', name, command], stdout=subprocess.PIPE, stderr=subprocess.PIPE) for
                 name, ip, mac in hosts]
    if not waithosts(hosts=hosts, port=8080, timeout=5):
        exit(1)
    print('servers started')
    return processes


def waithosts(hosts, port, timeout):
    for i in range(timeout * 4):
        time.sleep(1.0 / 4)
        # Ping {ip}:{port}
        returncodes = [subprocess.call(['nc', '-z', ip, str(port)]) for name, ip, mac in hosts]
        if min(returncodes) is 0:
            return True
    return False


def stophosts(hosts, command, processes):
    print('stopping servers')
    for name, ip, mac in hosts:
        urllib.urlopen(command.format(ip=ip))
    for process in processes:
        process.wait()
    print('servers stopped')


def run():
    import argparse
    import topology

    # Parse arguments
    parser = argparse.ArgumentParser()
    parser.add_argument("file", help="specify topology file", nargs='?')
    args = parser.parse_args()

    # Load topology
    filename = args.file if args.file else 'topologies/testbed.json'
    topo = topology.Topology(filename)

    # Start hosts
    processes = {
        'servers': starthosts(hosts=topo.servers, command=commands['server']['start']),
        'drivers': starthosts(hosts=topo.drivers, command=commands['driver']['start'])
    }

    # Run experiments
    time.sleep(1)

    # Stop hosts
    stophosts(hosts=topo.servers, command=commands['server']['stop'], processes=processes['servers'])
    stophosts(hosts=topo.drivers, command=commands['driver']['stop'], processes=processes['drivers'])


if __name__ == "__main__":
    run()
