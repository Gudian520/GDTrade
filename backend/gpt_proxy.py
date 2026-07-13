"""GD Trade GPT 研究代理服务。

启动前设置环境变量：
- OPENAI_API_KEY：OpenAI API Key，必填
- OPENAI_MODEL：模型名，默认 gpt-5.2
- GDTRADE_PROXY_HOST：监听地址，默认 0.0.0.0
- GDTRADE_PROXY_PORT：监听端口，默认 8787
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
DEFAULT_MODEL = "gpt-5.2"

SYSTEM_INSTRUCTIONS = """你是 GD Trade 的A股研究辅助后端。
边界：
- 不提供确定性买卖指令，不说必须买入、必须卖出。
- 不承诺收益，不预测确定价格。
- 必须指出风险、仓位和人工确认事项。
- V1 不自动提交证券交易订单。
- 输出中文，结构紧凑，重点包含潜力股票候选、观察理由、风险否决条件和人工确认清单。
"""


class GdTradeHandler(BaseHTTPRequestHandler):
    server_version = "GDTradeGPTProxy/0.1"

    def do_OPTIONS(self) -> None:
        self._send_empty(204)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send_json(200, {"ok": True, "service": "gdtrade-gpt-proxy"})
            return
        self._send_json(404, {"error": "未找到接口"})

    def do_POST(self) -> None:
        if self.path != "/gpt-advisor":
            self._send_json(404, {"error": "未找到接口"})
            return

        api_key = os.environ.get("OPENAI_API_KEY", "").strip()
        if not api_key:
            self._send_json(500, {"error": "后端未配置 OPENAI_API_KEY"})
            return

        try:
            request_body = self._read_json_body()
            prompt = str(request_body.get("prompt", "")).strip()
            if not prompt:
                self._send_json(400, {"error": "缺少 prompt"})
                return

            output = request_openai(api_key=api_key, prompt=prompt)
            self._send_text(200, output)
        except ValueError as exc:
            self._send_json(400, {"error": str(exc)})
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            self._send_json(exc.code, {"error": "OpenAI API 请求失败", "detail": detail})
        except Exception as exc:  # noqa: BLE001
            self._send_json(500, {"error": "代理服务异常", "detail": str(exc)})

    def _read_json_body(self) -> dict[str, Any]:
        content_length = int(self.headers.get("Content-Length", "0"))
        if content_length <= 0:
            raise ValueError("请求体为空")
        raw = self.rfile.read(content_length).decode("utf-8")
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError("请求体不是合法 JSON") from exc
        if not isinstance(data, dict):
            raise ValueError("请求体必须是 JSON 对象")
        return data

    def _send_empty(self, status: int) -> None:
        self.send_response(status)
        self._send_common_headers()
        self.end_headers()

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self._send_common_headers()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_text(self, status: int, content: str) -> None:
        body = content.encode("utf-8")
        self.send_response(status)
        self._send_common_headers()
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_common_headers(self) -> None:
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def log_message(self, fmt: str, *args: Any) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


def request_openai(api_key: str, prompt: str) -> str:
    model = os.environ.get("OPENAI_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL
    payload = {
        "model": model,
        "input": [
            {"role": "system", "content": SYSTEM_INSTRUCTIONS},
            {"role": "user", "content": prompt},
        ],
    }
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        OPENAI_RESPONSES_URL,
        data=data,
        method="POST",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        result = json.loads(response.read().decode("utf-8"))
    return extract_response_text(result)


def extract_response_text(result: dict[str, Any]) -> str:
    output_text = result.get("output_text")
    if isinstance(output_text, str) and output_text.strip():
        return output_text.strip()

    chunks: list[str] = []
    for item in result.get("output", []):
        if not isinstance(item, dict):
            continue
        for content in item.get("content", []):
            if isinstance(content, dict) and isinstance(content.get("text"), str):
                chunks.append(content["text"])
    text = "\n".join(chunk.strip() for chunk in chunks if chunk.strip())
    return text or "OpenAI 未返回可展示文本。"


def main() -> None:
    host = os.environ.get("GDTRADE_PROXY_HOST", "0.0.0.0")
    port = int(os.environ.get("GDTRADE_PROXY_PORT", "8787"))
    server = ThreadingHTTPServer((host, port), GdTradeHandler)
    print(f"GD Trade GPT 代理已启动：http://{host}:{port}/gpt-advisor")
    server.serve_forever()


if __name__ == "__main__":
    main()