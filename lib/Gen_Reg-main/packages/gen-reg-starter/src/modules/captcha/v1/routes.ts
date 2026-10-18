// src/modules/captcha/v1/routes.ts
import { randomBytes } from "node:crypto";
import { Router } from "express";

function tokenGenHtml(siteKey: string, nonce: string, cdnUrl: string): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <title>Turnstile Token Generator — Gen_REG dev</title>
  <script nonce="${nonce}" src="${cdnUrl}" async defer></script>
  <style>
    *, *::before, *::after { box-sizing: border-box; }
    body {
      font-family: system-ui, sans-serif;
      background: #0f1117;
      color: #e2e8f0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      margin: 0;
      gap: 24px;
      padding: 24px;
    }
    h1 { font-size: 1.25rem; font-weight: 600; margin: 0; }
    p  { font-size: 0.875rem; color: #94a3b8; margin: 0; text-align: center; }
    #widget-wrap {
      padding: 20px;
      background: #1e2532;
      border-radius: 12px;
      border: 1px solid #2d3748;
    }
    #result { display: none; flex-direction: column; gap: 12px; width: 100%; max-width: 560px; }
    #result label { font-size: 0.8rem; color: #94a3b8; text-transform: uppercase; letter-spacing: 0.05em; }
    #token-box {
      width: 100%;
      min-height: 80px;
      background: #1e2532;
      border: 1px solid #2d3748;
      border-radius: 8px;
      padding: 12px;
      color: #68d391;
      font-family: monospace;
      font-size: 0.8rem;
      word-break: break-all;
      white-space: pre-wrap;
      resize: none;
    }
    #copy-btn {
      align-self: flex-end;
      padding: 8px 20px;
      background: #3b82f6;
      color: #fff;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.875rem;
      font-weight: 500;
    }
    #copy-btn:hover { background: #2563eb; }
    #copy-btn.copied { background: #10b981; }
  </style>
</head>
<body>
  <h1>Turnstile Token Generator</h1>
  <p>Solve the challenge to get a token, then paste it into a POST /api/v1/signup request as "captchaToken".</p>

  <div id="widget-wrap">
    <div
      class="cf-turnstile"
      data-sitekey="${siteKey}"
      data-callback="onTokenReceived"
      data-theme="dark"
    ></div>
  </div>

  <div id="result">
    <label>Token — paste this as "captchaToken" in your signup request body</label>
    <textarea id="token-box" readonly></textarea>
    <button id="copy-btn">Copy token</button>
  </div>

  <p style="font-size:0.8rem;color:#64748b;text-align:center;max-width:480px">
    Token is single-use and expires in ~5 minutes.
  </p>

  <script nonce="${nonce}">
    function onTokenReceived(token) {
      var box = document.getElementById('token-box');
      var result = document.getElementById('result');
      box.value = token;
      result.style.display = 'flex';
      navigator.clipboard.writeText(token).catch(function() {});
    }

    document.getElementById('copy-btn').addEventListener('click', function() {
      var token = document.getElementById('token-box').value;
      var btn = document.getElementById('copy-btn');
      navigator.clipboard.writeText(token).then(function() {
        btn.textContent = 'Copied!';
        btn.classList.add('copied');
        setTimeout(function() {
          btn.textContent = 'Copy token';
          btn.classList.remove('copied');
        }, 2000);
      });
    });
  </script>
</body>
</html>`;
}

export function captchaDevRoutes(siteKey: string, cdnUrl: string): Router {
  const router = Router();

  router.get("/captcha/token-gen", (_req, res) => {
    const nonce = randomBytes(16).toString("base64");
    const cdnOrigin = new URL(cdnUrl).origin;
    res.setHeader("Content-Type", "text/html; charset=utf-8");
    res.setHeader(
      "Content-Security-Policy",
      [
        `script-src 'nonce-${nonce}' ${cdnOrigin}`,
        `frame-src ${cdnOrigin}`,
        `default-src 'self'`,
        `style-src 'unsafe-inline'`,
      ].join("; "),
    );
    res.send(tokenGenHtml(siteKey, nonce, cdnUrl));
  });

  return router;
}
