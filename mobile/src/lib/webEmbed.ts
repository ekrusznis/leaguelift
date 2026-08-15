export type WebEmbedFlow = 'owner-onboarding';

/** Builds the router target for /web-embed (ADR-106) and keeps its params shape in one place. */
export function webEmbedRoute(path: string, title: string, flow?: WebEmbedFlow) {
  return {
    pathname: '/web-embed' as const,
    params: flow ? { path, title, flow } : { path, title },
  };
}
