/** Builds the router.push target for /web-embed (ADR-106) — keeps the params shape in one place. */
export function webEmbedRoute(path: string, title: string) {
  return { pathname: '/web-embed' as const, params: { path, title } };
}
