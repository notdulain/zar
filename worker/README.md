# Zar quick-note Worker

Start with [deployment and integration instructions](../docs/openrouter-quick-notes.md).

```sh
npm ci
npm run check
npm test
npm run dev
```

`cloudflared` is not needed. Wrangler deploys this backend directly to the existing Cloudflare account.
OpenRouter keys belong in Worker secrets or the ignored `.dev.vars` file, never in the APK or source.
