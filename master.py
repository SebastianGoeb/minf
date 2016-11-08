#!/usr/bin/python2

import subprocess
import time
import urllib

controllercommand = 'java -jar ~/minf/floodlight/target/floodlight.jar'
drivercommand = 'python2 ~/minf/driver/driver.py'

commands = {
    'server': {
        'start': 'java -jar ~/minf/server/target/server-jar-with-dependencies.jar',
        'stop': 'wget -O - http://{ip}:8080/stop'
    },
    'driver': {
        'start': 'python2 ~/minf/driver/driver.py',
        'stop': 'wget -O - http://{ip}:8080/stop'
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
    parser.add_argument("-m", "--mininet", action="store_true", help="use mininet instead of testbed")
    # args = parser.parse_args()

    # Start hosts
    topo = topology.mininet  # if args.mininet else topology.testbed
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
