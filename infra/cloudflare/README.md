# Cloudflare

**Deferred — not part of the current prod deployment.** Per ADR-008
(`docs/adr/ADR-008-digitalocean-deployment.md`), DNS for `rally26.com` (the actual
registered domain — this file previously and incorrectly targeted `rally26.com`)
is handled directly by DigitalOcean DNS instead, to minimize the number of
accounts/tools needed for the first prod deploy. See `infra/digitalocean/README.md`.

This remains a clean later addition — putting Cloudflare in front of DO DNS for
edge-level rate limiting/bot mitigation/WAF on the public checkout and webhook
endpoints doesn't require undoing anything in ADR-008, just adding a proxy layer in
front of the existing droplet. Worth revisiting before scaling past a small pilot,
since nothing today protects those public endpoints at the edge.
