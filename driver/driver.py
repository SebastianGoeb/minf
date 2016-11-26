#!/usr/bin/env python2

# standard libraries
try:
    from http.server import BaseHTTPRequestHandler, HTTPServer
except ImportError:
    from BaseHTTPServer import BaseHTTPRequestHandler, HTTPServer
from threading import Thread, Event, Timer
from logging import debug, info, warning, error, basicConfig, INFO
import subprocess
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
        start_time = 0
        for shape in self.shapes:
            shape['startTime'] = start_time
            start_time += shape['duration']

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
            proc = self.start_proc(self.shapes[0])
            self.procs[proc.pid] = proc

        # Star timer
        info('start timer')
        self.timer.start()

        # Restart processes as they finish
        start_time = time.time()
        while self.procs:
            pid, retval = os.wait()
            del self.procs[pid]
            if not self.aborted.is_set():
                elapsed_time = time.time() - start_time
                shape = [s for s in self.shapes if s['startTime'] < elapsed_time][-1]
                proc = self.start_proc(shape)
                self.procs[proc.pid] = proc
        info('experiment done')

    def start_proc(self, shape):
        # Generate src ip
        weights = [distribution['weight'] for distribution in shape['src']]
        src_shape = shape['src'][np.random.choice(a=len(weights), p=weights)]
        kwargs = {k: v for k, v in src_shape.items() if k != 'name' and k != 'weight'}
        distribution = getattr(stats, src_shape['name'])(**kwargs)
        src = num2ip(distribution.rvs())
        # Start process
        info('wget -O - --limit-rate {rate} --bind-address {src} http://{dst}:8080/{size}'
             .format(rate=shape['rate'], src=src, dst=self.dst, size=shape['size']))
        return subprocess.Popen(['sleep', str(shape['size'] / shape['rate'])])
        # return subprocess.Popen(['wget', '-O', '-', '--limit-rate', str(shape['rate']),
        #     '--bind-address', src, 'http://{dst}:8080/{size}'.format(dst=self.dst, size=shape['size'])])


class RequestHandler(BaseHTTPRequestHandler):
    experiment = None

    # noinspection PyPep8Naming
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

    # noinspection PyPep8Naming
    def do_DELETE(self):
        match = re.compile(r'/experiment').match(self.path)
        if match:
            if self.experiment:
                self.experiment.abort()
                self.send_response(200)
            else:
                self.send_response(400)

            t = Timer(0, lambda: server.shutdown())
            t.start()
        else:
            self.send_response(404)

    def stop(self):
        pass


def num2ip(num):
    raw = int(num * (1 << 32))
    return '{}.{}.{}.{}'.format((raw >> 24) & 255, (raw >> 16) & 255, (raw >> 8) & 255, raw & 255)


if __name__ == "__main__":
    import argparse

    # Parse arguments
    parser = argparse.ArgumentParser()
    parser.add_argument('ip', help='specify ip address to listen on', nargs='?')
    args = parser.parse_args()

    # Configure logging
    basicConfig(level=INFO, format='%(relativeCreated)6d\t%(message)s')

    # Run server
    ip = args.ip if args.ip else '0.0.0.0'
    info('serving {} 8080'.format(ip))
    server = HTTPServer((ip, 8080), RequestHandler)
    server.serve_forever()
