/** Client-generated idempotency keys for message/reply sends — just needs to be unique per attempt, not cryptographically random. */
export function generateIdempotencyKey(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}
