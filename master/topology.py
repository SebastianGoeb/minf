import json
import os


class Topology(object):
    def __init__(self, filename):
        if not os.path.isfile(filename):
            print('Sorry, no such file')
            exit(1)
        topo = json.loads(open(filename, 'r').read())
        domain = '.' + topo['domain'] if 'domain' in topo else ''
        self.controllers = [(name + domain, ip, mac) for name, ip, mac in topo['controllers']]
        self.drivers = [(name + domain, ip, mac) for name, ip, mac in topo['drivers']]
        self.servers = [(name + domain, ip, mac) for name, ip, mac in topo['servers']]
