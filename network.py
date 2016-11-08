#!/usr/bin/python2

"""
Create a network and start sshd(8) on each host.

While something like rshd(8) would be lighter and faster,
(and perfectly adequate on an in-machine network)
the advantage of running sshd is that scripts can work
unchanged on mininet and hardware.

In addition to providing ssh access to hosts, this example
demonstrates:

- creating a convenience function to construct networks
- connecting the host network to the root namespace
- running server processes (sshd in this case) on hosts
"""

import sys
import re

from mininet.net import Mininet
from mininet.cli import CLI
from mininet.log import setLogLevel, info
from mininet.node import Node, RemoteController
from mininet.topo import Topo
from mininet.topolib import TreeTopo
from mininet.util import waitListening

class TestbedTopo( Topo ):
    "Topology for a simplified version of the UoE testbed"

    def build( self, drivers=1 ):
        info( '*** Creating testbed network\n' )
        # Hosts
        controller = self.addHost( 'c0' )
        driver0 = self.addHost( 'd0' )
        server0 = self.addHost( 's0' )
        server1 = self.addHost( 's1' )
        # Management network
        switch0 = self.addSwitch( 'br0' )
        self.addLink( controller, switch0 )
        self.addLink( driver0, switch0 )
        self.addLink( server0, switch0 )
        self.addLink( server1, switch0 )
        # Data network
        switch1 = self.addSwitch( 'br1' )
        # self.addLink( controller, switch1 )
        self.addLink( driver0, switch1 )
        self.addLink( server0, switch1 )
        self.addLink( server1, switch1 )

        info( '*** Creating management network\n' )
        # self.addSwitch( 'ms0' )

def TestbedNet( drivers=1, **kwargs ):
    "Convenience function for creating testbed topo network."
    topo = TestbedTopo( drivers )
    return Mininet( topo=topo, ipBase='192.168.123.0/24', **kwargs )

def connectToRootNS( network, switch, ip, routes ):
    """Connect hosts to root namespace via switch. Starts network.
      network: Mininet() network object
      switch: switch to connect to root namespace
      ip: IP address for root namespace node
      routes: host networks to route to"""
    # Create a node in root namespace and link to switch 0
    root = Node( 'root', inNamespace=False )
    intf = network.addLink( root, switch ).intf1
    root.setIP( ip, intf=intf )
    # Start network that now includes link to root namespace
    network.start()

    # (nsl002)
    # c0 = network.get('c0')
    # c0.setIP( '10.0.1.2/8', intf='c0-eth1' )

    # (nsl003)
    s0 = network.get('s0')
    s0.setIP( '10.1.1.2/8', intf='s0-eth1' )

    # (nsl004)
    s1 = network.get('s1')
    s1.setIP( '10.1.1.3/8', intf='s1-eth1' )
    # Add routes from root ns to hosts
    for route in routes:
        root.cmd( 'route add -net ' + route + ' dev ' + str( intf ) )

def sshd( network, cmd='/usr/sbin/sshd', opts='-D',
          ip=None, routes=None, switch=None ):
    """Start a network, connect it to root ns, and run sshd on all hosts.
       ip: root-eth0 IP address in root namespace (x.x.x.64/32)
       routes: Mininet host networks to route to (x.x.x.x/24)
       switch: Mininet switch to connect to root namespace (s1)"""
    if not switch:
        switch = network[ 'br0' ]  # switch to use
    if not routes:
        routes = [ network.ipBase ]
    if not ip:
        ip = re.sub(r'\d+/\d+', '64/32', network.ipBase)
    connectToRootNS( network, switch, ip, routes )
    for host in network.hosts:
        host.cmd( cmd + ' ' + opts + '&' )
    print "*** Waiting for ssh daemons to start"
    for server in network.hosts:
        waitListening( server=server, port=22, timeout=5 )

    print
    print "*** Hosts are running sshd at the following addresses:"
    print
    for host in network.hosts:
        print host.name, host.IP()
    print
    print "*** Type 'exit' or control-D to shut down network"

    c = network.addController( 'bla', controller=RemoteController, ip='192.168.56.102', port=6634 )
    c.start()

    CLI( network )
    for host in network.hosts:
        host.cmd( 'kill %' + cmd )
    network.stop()

if __name__ == '__main__':
    setLogLevel( 'info')
    net = TestbedNet( drivers=1 )
    # get sshd args from the command line or use default args
    # useDNS=no -u0 to avoid reverse DNS lookup timeout
    argvopts = ' '.join( sys.argv[ 1: ] ) if len( sys.argv ) > 1 else (
        '-D -o UseDNS=no -u0' )
    sshd( network=net, opts=argvopts )
