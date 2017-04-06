#!/usr/bin/env python2

from __future__ import print_function

import sys
import re
import json


def cidr2int(cidr):
    return int('1' * int(cidr) + '0' * (32 - int(cidr)), 2)

def ip2int(ip):
    if '/' in ip:
        ip, cidr = ip.split('/')
        return ip2int(ip), cidr2int(cidr)
    else:
        octets = [int(octet) for octet in ip.split('.')]
        return sum(octet * (1 << (24 - idx * 8)) for idx, octet in enumerate(octets))

def extractServerByteCounts(datum):
    return {msmt['prefix'].split('/')[0]: msmt['bytes'] for msmt in datum['serverMeasurements']}

def calculateServerRates(serverByteCounts, prevServerByteCounts=None, interval=None):
    if not prevServerByteCounts or not interval:
        return {ip: float(0) for ip, byteCount in serverByteCounts.iteritems()}
    return {ip: float(byteCount - prevServerByteCounts[ip]) / (interval / 1000.0) for ip, byteCount in serverByteCounts.iteritems()}

def calculateLoadImbalance(serverRates):
    rates = serverRates.values()
    max_rate = max(rates)
    avg_rate = sum(rates) / len(rates)
    if avg_rate == 0:
        return None
    else:
        return max_rate / avg_rate - 1


# Parse data
snapshotRegex = re.compile(r'snapshot: (.*)')
matchObjects = [snapshotRegex.search(line.strip()) for line in sys.stdin]
rawData = [json.loads(mo.group(1)) for mo in matchObjects if mo]

if rawData:
    # One-off calculations
    timestampOffset = rawData[0]['timestamp']

    # Process data
    data = []
    for rawDatum in rawData:
        timestamp = rawDatum['timestamp'] - timestampOffset
        numRules = rawDatum['numRules']
        serverByteCounts = extractServerByteCounts(rawDatum)
        serverRates = calculateServerRates(serverByteCounts, data[-1]['serverByteCounts'] if data else None, timestamp - data[-1]['timestamp'] if data else None)
        if not serverRates:
            continue
        loadImbalance = calculateLoadImbalance(serverRates)

        # Package and append processed datum
        datum = {
            'timestamp': timestamp,
            'numRules': numRules,
            'serverByteCounts': serverByteCounts,
            'serverRates': serverRates,
            'loadImbalance': loadImbalance
        }
        data.append(datum)

    while data and not data[0]['loadImbalance']:
        del data[0]

    if not data:
        exit(0)

    timestampOffset = data[0]['timestamp']

    # Turn in tsv
    headers = ['Timestamp']
    headers += ['Rules: ' + dpid for dpid in sorted(data[0]['numRules'].keys())]
    headers += ['Rate: ' + ip for ip in sorted(data[0]['serverRates'].keys(), key=lambda x: ip2int(x))]
    headers += ['Load Imbalance']
    print('\t'.join(headers))

    # Print data
    for datum in data:
        row = [str(round((datum['timestamp'] - timestampOffset) / 1000.0, 1))]
        row += [str(rules) for dpid, rules in sorted(datum['numRules'].iteritems())]
        row += [str(rate) for ip, rate in sorted(datum['serverRates'].iteritems(), key=lambda x: ip2int(x[0]))]
        row += [str(datum['loadImbalance']) if datum['loadImbalance'] else 'nan']
        print('\t'.join(row))
