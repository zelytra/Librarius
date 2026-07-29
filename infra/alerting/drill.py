#!/usr/bin/env python3
"""Two throwaway HTTP servers used by fire-drill.sh, and nothing else.

`target` impersonates the Quarkus API well enough for the three API rules to be
exercised for real: it exposes /q/metrics with Micrometer's own metric names, a
5xx share above the 5% threshold, and latency histograms whose 95th percentile
sits above the 2 s threshold. Stopping it is what makes `up` go to zero.

`sink` is the notification destination. It prints every payload Alertmanager
posts to it, one JSON object per line, which is what the drill greps to decide
whether an alert really left the stack.

No dependency beyond the standard library: the drill runs in python:3-alpine.
"""

import json
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

START = time.time()

# Requests per second the fake API pretends to serve. 3 of every 13 are 5xx —
# 23%, comfortably over the 5% LibrariusApiHighErrorRate fires at, so the drill
# does not depend on rounding.
OK_RATE = 10.0
ERR_RATE = 3.0

# Every request is answered in more than 2 s and less than 5 s, so the p95 that
# LibrariusApiSlowResponses computes lands at 4.85 s: over the threshold by a
# margin no interpolation can talk away.
BUCKETS = ["0.1", "0.5", "1.0", "2.0", "5.0", "+Inf"]
FILLED_FROM = 4  # index of le="5.0": every observation falls in (2, 5]
LATENCY = 3.5


def exposition() -> str:
    elapsed = time.time() - START
    lines = [
        "# HELP http_server_requests_seconds Duration of HTTP server request handling",
        "# TYPE http_server_requests_seconds histogram",
    ]
    for status, rate in (("200", OK_RATE), ("500", ERR_RATE)):
        count = int(elapsed * rate)
        labels = f'method="GET",uri="/api/library",status="{status}"'
        for index, le in enumerate(BUCKETS):
            value = count if index >= FILLED_FROM else 0
            lines.append(
                f'http_server_requests_seconds_bucket{{{labels},le="{le}"}} {value}'
            )
        lines.append(f"http_server_requests_seconds_count{{{labels}}} {count}")
        lines.append(
            f"http_server_requests_seconds_sum{{{labels}}} {count * LATENCY:.1f}"
        )
    return "\n".join(lines) + "\n"


class Target(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802 - imposed by BaseHTTPRequestHandler
        if self.path != "/q/metrics":
            self.send_error(404)
            return
        body = exposition().encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class Sink(BaseHTTPRequestHandler):
    def do_POST(self):  # noqa: N802 - imposed by BaseHTTPRequestHandler
        raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {"status": "unparseable", "raw": raw.decode("utf-8", "replace")}
        # One line per notification, flushed: the drill reads this from
        # `docker compose logs` while the stack is still running.
        print(json.dumps(payload, separators=(",", ":")), flush=True)
        self.send_response(200)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    role = sys.argv[1] if len(sys.argv) > 1 else ""
    handler = {"target": Target, "sink": Sink}.get(role)
    if handler is None:
        sys.exit("usage: drill.py target|sink")
    HTTPServer(("0.0.0.0", 8080), handler).serve_forever()
