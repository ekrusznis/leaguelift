import test from 'node:test';
import assert from 'node:assert/strict';

import {
  dateSeparatorLabel,
  formatRelativeListTime,
  isDifferentDay,
  shouldGroupWithPrevious,
} from '../src/features/messaging/dateFormat.ts';

const NOW = new Date('2026-08-20T16:30:00Z');

test('formatRelativeListTime shows a time-of-day for a message sent today', () => {
  const result = formatRelativeListTime('2026-08-20T16:26:00Z', NOW);
  assert.match(result, /\d{1,2}:\d{2}\s?(AM|PM)/);
});

test('formatRelativeListTime shows "Yesterday" for a message sent the day before', () => {
  assert.equal(formatRelativeListTime('2026-08-19T10:00:00Z', NOW), 'Yesterday');
});

test('formatRelativeListTime shows a short weekday within the last week', () => {
  const result = formatRelativeListTime('2026-08-17T10:00:00Z', NOW);
  assert.match(result, /^(Mon|Tue|Wed|Thu|Fri|Sat|Sun)$/);
});

test('formatRelativeListTime shows a short date for anything older than a week', () => {
  assert.equal(formatRelativeListTime('2026-07-01T10:00:00Z', NOW), 'Jul 1');
});

test('dateSeparatorLabel returns TODAY / YESTERDAY / a short date', () => {
  assert.equal(dateSeparatorLabel('2026-08-20T09:00:00Z', NOW), 'TODAY');
  assert.equal(dateSeparatorLabel('2026-08-19T09:00:00Z', NOW), 'YESTERDAY');
  assert.equal(dateSeparatorLabel('2026-08-01T09:00:00Z', NOW), 'AUG 1');
});

test('isDifferentDay distinguishes calendar days regardless of time', () => {
  // A 2-day gap can never disagree about "different calendar day" across any real
  // timezone offset, unlike a same-UTC-day-but-near-midnight pair (timezone-fragile).
  assert.equal(isDifferentDay('2026-08-20T12:00:00Z', '2026-08-20T12:05:00Z'), false);
  assert.equal(isDifferentDay('2026-08-20T12:00:00Z', '2026-08-22T12:00:00Z'), true);
});

test('shouldGroupWithPrevious merges consecutive same-sender messages within the window', () => {
  const previous = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:00:00Z' };
  const current = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:03:00Z' };
  assert.equal(shouldGroupWithPrevious(current, previous), true);
});

test('shouldGroupWithPrevious does not merge across different senders', () => {
  const previous = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:00:00Z' };
  const current = { senderUserId: 'parent-1', sentAt: '2026-08-20T16:01:00Z' };
  assert.equal(shouldGroupWithPrevious(current, previous), false);
});

test('shouldGroupWithPrevious does not merge once the window has elapsed', () => {
  const previous = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:00:00Z' };
  const current = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:06:00Z' };
  assert.equal(shouldGroupWithPrevious(current, previous), false);
});

test('shouldGroupWithPrevious returns false with no previous message', () => {
  const current = { senderUserId: 'coach-1', sentAt: '2026-08-20T16:00:00Z' };
  assert.equal(shouldGroupWithPrevious(current, undefined), false);
});
