#!/usr/bin/env bash

ssh  nsl105 <<EOF
echo "Recreate br1"
ovs-vsctl del-br br1
ovs-vsctl add-br br1 -- set bridge br1 datapath_type=pica8 other-config=datapath-id=0000000000000001
ovs-vsctl set-controller br1 tcp:129.215.164.111:6653
ovs-vsctl -- set bridge br1 protocols=OpenFlow13
ovs-vsctl add-port br1 ge-1/1/1 vlan_mode=trunk -- set interface ge-1/1/1 type=pica8
ovs-vsctl add-port br1 ge-1/1/2 vlan_mode=trunk -- set interface ge-1/1/2 type=pica8
ovs-vsctl add-port br1 ge-1/1/3 vlan_mode=trunk -- set interface ge-1/1/3 type=pica8
ovs-vsctl add-port br1 ge-1/1/4 vlan_mode=trunk -- set interface ge-1/1/4 type=pica8

echo "Recreate br2"
ovs-vsctl del-br br2
ovs-vsctl add-br br2 -- set bridge br2 datapath_type=pica8 other-config=datapath-id=0000000000000002
ovs-vsctl set-controller br2 tcp:129.215.164.111:6633
ovs-vsctl -- set bridge br2 protocols=OpenFlow13
ovs-vsctl add-port br2 ge-1/1/5 vlan_mode=trunk -- set interface ge-1/1/5 type=pica8
ovs-vsctl add-port br2 ge-1/1/6 vlan_mode=trunk -- set interface ge-1/1/6 type=pica8
ovs-vsctl add-port br2 ge-1/1/7 vlan_mode=trunk -- set interface ge-1/1/7 type=pica8
ovs-vsctl add-port br2 ge-1/1/8 vlan_mode=trunk -- set interface ge-1/1/8 type=pica8

echo "Populate flows for br1"
ovs-ofctl add-flow br2 priority=2,ip,nw_dst=10.1.2.2/32,actions=output:5
ovs-ofctl add-flow br2 priority=2,ip,nw_dst=10.1.2.3/32,actions=output:6
ovs-ofctl add-flow br2 priority=1,ip,nw_dst=10.0.0.0/8,actions=output:8
ovs-ofctl add-flow br2 priority=0,actions=drop
EOF

ssh  nsl103 <<EOF
echo "Recreate br9"
ovs-vsctl del-br br9
ovs-vsctl add-br br9 -- set bridge br9 datapath_type=pica8 other-config=datapath-id=0000000000000009
ovs-vsctl set-controller br9 tcp:129.215.164.111:6633
ovs-vsctl -- set bridge br9 protocols=OpenFlow13
ovs-vsctl add-port br9 ge-1/1/1 vlan_mode=trunk -- set interface ge-1/1/1 type=pica8
ovs-vsctl add-port br9 ge-1/1/2 vlan_mode=trunk -- set interface ge-1/1/2 type=pica8
ovs-vsctl add-port br9 ge-1/1/3 vlan_mode=trunk -- set interface ge-1/1/3 type=pica8
ovs-vsctl add-port br9 ge-1/1/4 vlan_mode=trunk -- set interface ge-1/1/4 type=pica8
ovs-vsctl add-port br9 ge-1/1/17 vlan_mode=trunk -- set interface ge-1/1/17 type=pica8

echo "Populate flows for br9"
ovs-ofctl add-flow br9 priority=2,ip,nw_dst=10.5.1.12/32,actions=output:1
ovs-ofctl add-flow br9 priority=1,ip,nw_dst=10.0.0.0/8,actions=output:17
ovs-ofctl add-flow br9 priority=0,actions=drop

echo "Recreate br10"
ovs-vsctl del-br br10
ovs-vsctl add-br br10 -- set bridge br10 datapath_type=pica8 other-config=datapath-id=000000000000000A
ovs-vsctl set-controller br10 tcp:129.215.164.111:6633
ovs-vsctl -- set bridge br10 protocols=OpenFlow13
ovs-vsctl add-port br10 ge-1/1/5 vlan_mode=trunk -- set interface ge-1/1/5 type=pica8
ovs-vsctl add-port br10 ge-1/1/6 vlan_mode=trunk -- set interface ge-1/1/6 type=pica8
ovs-vsctl add-port br10 ge-1/1/7 vlan_mode=trunk -- set interface ge-1/1/7 type=pica8
ovs-vsctl add-port br10 ge-1/1/8 vlan_mode=trunk -- set interface ge-1/1/8 type=pica8

echo "Populate flows for br10"
ovs-ofctl add-flow br10 priority=2,ip,nw_dst=10.1.2.2/31,actions=output:6
ovs-ofctl add-flow br10 priority=1,ip,nw_dst=10.0.0.0/8,actions=output:5
ovs-ofctl add-flow br10 priority=0,actions=drop
EOF
