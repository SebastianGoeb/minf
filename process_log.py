#!/usr/bin/env python2

import sys
import re
from datetime import datetime

reg = re.compile(r'.*timestamp: (.*)\tbytes: (.*)\trates: (.*)')

for line in sys.stdin:
    m = reg.match(line.strip())
    if m:
        timestamp = m.group(1)
        rates = [float(part.split('=')[1]) for part in m.group(3).split(',')]
        max_rate = max(rates)
        avg_rate = sum(rates) / len(rates)
        imbalance = max_rate / avg_rate - 1 if max_rate > 0 else 0
        print("%s\t[%s]\t%0.3f" % (datetime.fromtimestamp(float(timestamp) / 1000), ', '.join("%0.2f" % r for r in rates), imbalance))
