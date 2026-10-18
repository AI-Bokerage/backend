# gen-tnt-demo/scripts/mock_step_server.py
"""Tiny stdlib-only HTTP stub standing in for real provisioning step targets.
Auto-completes every sync step call with 200, and every async step call with
202 followed by a background callback POST back to Gen_TNT after a short delay."""
import json
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

GEN_TNT_BASE = "http://localhost:8201"


class Handler(BaseHTTPRequestHandler):
    # ponytail: HTTP/1.1 + explicit Content-Length, not the http.server default
    # (HTTP/1.0, close-delimited body) — Spring's JDK-backed RestClient throws
    # a spurious "http1_0 content" IOException against a close-delimited body
    # on Windows even when every byte arrived, so give it a real frame instead.
    protocol_version = "HTTP/1.1"

    def _read_body(self):
        # ponytail: Spring's JDK-backed RestClient streams JSON bodies as
        # Transfer-Encoding: chunked (no Content-Length) rather than buffering
        # them up front — http.server has no built-in chunked-request decoder,
        # so without this the body silently reads back empty. Content-Length
        # path kept for any client (e.g. curl) that sends a fixed-length body.
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            chunks = []
            while True:
                size_line = self.rfile.readline().strip()
                size = int(size_line.split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()  # trailing CRLF after last chunk
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()  # CRLF after each chunk's data
            return b"".join(chunks)
        length = int(self.headers.get("Content-Length", 0))
        return self.rfile.read(length)

    def do_POST(self):
        raw = self._read_body()
        body = json.loads(raw or b"{}")

        if self.path == "/sync-step":
            payload = json.dumps({"schemaReady": True}).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        if self.path == "/async-step":
            self.send_response(202)
            self.send_header("Content-Length", "0")
            self.end_headers()
            job_id = body.get("jobId")
            token = body.get("callbackToken")
            threading.Thread(target=self._fire_callback, args=(job_id, token), daemon=True).start()
            return

        self.send_response(404)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def _fire_callback(self, job_id, token):
        time.sleep(1)
        payload = json.dumps({"token": token, "success": True, "context": {"adminUserId": "mock-admin-1"}}).encode()
        req = urllib.request.Request(
            f"{GEN_TNT_BASE}/internal/provisioning/jobs/{job_id}/steps/AUTH_BOOTSTRAP/callback",
            data=payload,
            headers={"Content-Type": "application/json", "X-Internal-Secret": "dev-secret"},
            method="POST",
        )
        urllib.request.urlopen(req)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("localhost", 9001), Handler).serve_forever()
