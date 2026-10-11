#!/usr/bin/env python3
"""Deploy trigger for the CD job.

The provider drops inbound 22/80/443 from every network outside Vietnam, and it
also blocks Microsoft's anycast range, so neither a GitHub-hosted runner (SSH)
nor a self-hosted one (job polling) can reach this box. The Cloudflare tunnel is
the only way in and it only carries HTTP - hence this hook.

One deploy can outlive Cloudflare's 100s origin timeout, so POST starts a job and
returns immediately; the caller polls /status/<id> until it finishes.
"""

import hmac
import json
import os
import re
import subprocess
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BASE = "/opt/fintech"
ENV_FILE = f"{BASE}/infra/docker/.env"
DEPLOY = f"{BASE}/infra/scripts/deploy.sh"

SERVICES = {
	"eureka-server",
	"api-gateway",
	"transaction-engine",
	"account-ledger-service",
	"payment-gateway-service",
	"notification-service",
	"audit-service",
}
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
JOB_ID_RE = re.compile(r"^/status/([0-9a-f]{32})$")
DEPLOY_TIMEOUT = 900
KEEP_JOBS = 20

_jobs = {}
_jobs_lock = threading.Lock()


def expected_token():
	with open(ENV_FILE, encoding="utf-8") as fh:
		for line in fh:
			name, _, value = line.strip().partition("=")
			if name == "DEPLOY_HOOK_TOKEN":
				return value
	return ""


def run_deploy(job_id, service, sha):
	try:
		proc = subprocess.run(
			[DEPLOY, service, sha],
			capture_output=True,
			text=True,
			timeout=DEPLOY_TIMEOUT,
		)
		state = "done" if proc.returncode == 0 else "failed"
		log = (proc.stdout + proc.stderr)[-8000:]
		code = proc.returncode
	except subprocess.TimeoutExpired:
		state, log, code = "failed", f"deploy.sh exceeded {DEPLOY_TIMEOUT}s", -1
	except OSError as exc:
		state, log, code = "failed", f"cannot run deploy.sh: {exc}", -1
	with _jobs_lock:
		_jobs[job_id] = {"state": state, "exit": code, "log": log}


class Handler(BaseHTTPRequestHandler):
	server_version = "fintech-deploy-hook"

	def _reply(self, code, payload):
		body = json.dumps(payload).encode()
		self.send_response(code)
		self.send_header("Content-Type", "application/json")
		self.send_header("Content-Length", str(len(body)))
		self.end_headers()
		self.wfile.write(body)

	def _authorized(self):
		token = expected_token()
		supplied = self.headers.get("Authorization", "")
		return bool(token) and hmac.compare_digest(supplied, f"Bearer {token}")

	def do_POST(self):
		if self.path != "/deploy":
			return self._reply(404, {"error": "not found"})
		if not self._authorized():
			return self._reply(401, {"error": "unauthorized"})
		try:
			length = int(self.headers.get("Content-Length") or 0)
			payload = json.loads(self.rfile.read(length) or b"{}")
			service = payload["service"]
			sha = payload["sha"]
		except (ValueError, KeyError, TypeError):
			return self._reply(400, {"error": "expected JSON {service, sha}"})
		if service not in SERVICES:
			return self._reply(400, {"error": f"unknown service: {service!r}"})
		if not SHA_RE.match(sha):
			return self._reply(400, {"error": "sha must be 40 lowercase hex chars"})

		job_id = uuid.uuid4().hex
		with _jobs_lock:
			_jobs[job_id] = {"state": "running", "exit": None, "log": ""}
			for stale in list(_jobs)[:-KEEP_JOBS]:
				del _jobs[stale]
		threading.Thread(target=run_deploy, args=(job_id, service, sha), daemon=True).start()
		print(f"started {job_id} for {service}@{sha}", flush=True)
		return self._reply(202, {"id": job_id})

	def do_GET(self):
		match = JOB_ID_RE.match(self.path)
		if not match:
			return self._reply(404, {"error": "not found"})
		with _jobs_lock:
			job = _jobs.get(match.group(1))
		if job is None:
			return self._reply(404, {"error": "unknown job"})
		return self._reply(200, job)

	def log_message(self, fmt, *args):
		print(fmt % args, flush=True)


if __name__ == "__main__":
	ThreadingHTTPServer(("127.0.0.1", 9000), Handler).serve_forever()
