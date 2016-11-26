#!/usr/bin/env python2

# standard libraries
from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
from threading import Thread, Event, Timer
from logging import debug, info, warning, error, basicConfig, INFO
import subprocess
import bisect
import time
import json
import os
import re

# pip libraries
import numpy as np
import scipy.stats as stats


'''
Sample Spec:
{
    "dst": "10.0.0.1",
    "shapes": [{
        "src": [{
            "name": "uniform",
            "loc": 0,
            "scale": 1,
            "weight": 0.25
        }, {
            "name": "truncnorm",
            "loc": 0.5,
            "scale": 0.1,
            "weight": 0.75
        }],
        "rate": "1G",
        "size": "6G",
        "clients": 20,
        "duration": 10
    }]
}
'''


class Experiment(Thread):

    def __init__(self, shapes, dst):
        super(Experiment, self).__init__()
        self.aborted = Event()
        self.procs = {}
        self.dst = dst
        self.shapes = shapes
        # Setup timer
        self.timer = Timer(sum(s['duration'] for s in self.shapes), self.abort)
        # Calculate start times
        startTime = 0
        for shape in self.shapes:
            shape['startTime'] = startTime
            startTime += shape['duration']

    def abort(self):
        self.timer.cancel()
        self.aborted.set()
        for pid, proc in self.procs.items():
            proc.kill()
        self.join()

    def run(self):
        # Start processes
        info('start processes')
        for _ in range(self.shapes[0]['clients']):
            proc = self.startProc(self.shapes[0])
            self.procs[proc.pid] = proc

        # Star timer
        info('start timer')
        self.timer.start()

        # Restart processes as they finish
        startTime = time.time()
        while self.procs:
            pid, retval = os.wait()
            del self.procs[pid]
            if not self.aborted.is_set():
                elapsedTime = time.time() - startTime
                shape = [s for s in self.shapes if s['startTime'] < elapsedTime][-1]
                proc = self.startProc(shape)
                self.procs[proc.pid] = proc
        info('experiment done')

    def startProc(self, shape):
        # Generate src ip
        weights = [distribution['weight'] for distribution in shape['src']]
        srcShape = shape['src'][np.random.choice(a=len(weights), p=weights)]
        kwargs = {k: v for k, v in srcShape.items() if k != 'name' and k != 'weight'}
        distribution = getattr(stats, srcShape['name'])(**kwargs)
        src = num2ip(distribution.rvs())
        # Start process
        return subprocess.Popen(['wget', '-O', '-', '--limit-rate', str(shape['rate']),
            '--bind-address', src, 'http://{dst}:8080/{size}'.format(dst=self.dst, size=shape['size'])])

class testHTTPServer_RequestHandler(BaseHTTPRequestHandler):
    experiment = None

    def do_POST(self):
        match = re.compile(r'/experiment').match(self.path)
        if match:
            if not self.experiment:
                contentLength = int(self.headers['Content-Length'])
                spec = json.loads(self.rfile.read(contentLength))
                self.experiment = Experiment(spec['shapes'], spec['dst'])
                self.experiment.start()
                self.send_response(200)
            else:
                self.send_response(400)
        else:
            self.send_response(404)

    def do_DELETE(self):
        match = re.compile(r'/experiment').match(self.path)
        if match:
            if self.experiment:
                self.experiment.abort()
                self.send_response(200)
            else:
                self.send_response(400)
            def stop():
                httpd.shutdown()
            t = Timer(0, stop)
            t.start()
        else:
            self.send_response(404)


# Util
def num2ip(num):
    raw = int(num * (1 << 32))
    return '{}.{}.{}.{}'.format((raw >> 24) & 255, (raw >> 16) & 255, (raw >> 8) & 255, raw & 255)


if __name__ == "__main__":
    basicConfig(level=INFO, format='%(relativeCreated)6d\t%(message)s')
    httpd = HTTPServer(('0.0.0.0', 8080), testHTTPServer_RequestHandler)
    info('serving 0.0.0.0 8080')
    httpd.serve_forever()
