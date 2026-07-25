import json
import logging
from http.server import BaseHTTPRequestHandler, HTTPServer
import urllib.request
import urllib.parse

# ---------------------------------------------------------------------------
# BATON Tool Server - Scaffolding
# Exposes contextual tools for the reasoning agent:
# 1. Dependency / CVE Lookups (OSV.dev integration)
# 2. Aggregated Gateway Logs
# ---------------------------------------------------------------------------

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("baton-tool-server")

class ToolServerHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers.get('Content-Length', 0))
        post_data = self.rfile.read(content_length)
        
        try:
            req = json.loads(post_data.decode('utf-8'))
            tool_name = req.get('tool')
            kwargs = req.get('kwargs', {})
            
            if tool_name == 'lookup_cve':
                result = self.lookup_cve(**kwargs)
            elif tool_name == 'fetch_gateway_logs':
                result = self.fetch_gateway_logs(**kwargs)
            else:
                self.send_error(400, f"Unknown tool: {tool_name}")
                return
                
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"result": result}).encode('utf-8'))
            
        except Exception as e:
            logger.error(f"Error executing tool: {e}")
            self.send_error(500, str(e))

    def lookup_cve(self, package_name: str, version: str, ecosystem: str = "Maven") -> dict:
        """
        Query OSV.dev for known vulnerabilities in a specific dependency.
        """
        logger.info(f"Looking up CVEs for {package_name}@{version} ({ecosystem})")
        # Scaffolded OSV API call
        query = {
            "version": version,
            "package": {
                "name": package_name,
                "ecosystem": ecosystem
            }
        }
        
        req = urllib.request.Request(
            "https://api.osv.dev/v1/query",
            data=json.dumps(query).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        
        try:
            with urllib.request.urlopen(req) as response:
                return json.loads(response.read().decode('utf-8'))
        except Exception as e:
            return {"error": str(e)}

    def fetch_gateway_logs(self, limit: int = 100) -> list:
        """
        Fetch aggregated Rust gateway logs for anomaly detection.
        """
        logger.info(f"Fetching last {limit} gateway logs")
        # In a real implementation, this would query ElasticSearch, Loki, or read from a local file
        return [
            {"timestamp": "2026-07-25T12:00:00Z", "level": "WARN", "msg": "Rate limit exceeded for IP 192.168.1.100"},
            {"timestamp": "2026-07-25T12:05:12Z", "level": "INFO", "msg": "Auth rejected: Unknown client key"},
            {"timestamp": "2026-07-25T12:08:44Z", "level": "INFO", "msg": "Payload received from user (4096B)"},
        ][:limit]

def run(port=8081):
    server_address = ('', port)
    httpd = HTTPServer(server_address, ToolServerHandler)
    logger.info(f"Starting BATON Tool Server on port {port}...")
    httpd.serve_forever()

if __name__ == '__main__':
    run()
