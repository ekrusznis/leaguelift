import type { EventResponse } from '@/features/events/types';

/** startAt/endAt are UTC ISO instants; format in the event's own IANA timezone, not the device's. */
export function formatEventTimeRange(event: EventResponse): string {
  if (event.allDayDate) return 'All day';
  if (!event.startAt) return 'Time TBD';
  const start = new Date(event.startAt);
  const startLabel = start.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: event.timezone });
  if (!event.endAt) return startLabel;
  const end = new Date(event.endAt);
  const endLabel = end.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: event.timezone });
  return `${startLabel} – ${endLabel}`;
}

export function formatEventDateHeader(event: EventResponse): string {
  if (event.allDayDate) {
    const [y, m, d] = event.allDayDate.split('-').map(Number);
    return new Date(y, m - 1, d).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
  }
  if (!event.startAt) return 'Date TBD';
  return new Date(event.startAt).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    timeZone: event.timezone,
  });
}

/** "YYYY-MM-DD" in the event's own timezone — used to group events by day on the Calendar agenda. */
export function eventIsoDate(event: EventResponse): string {
  if (event.allDayDate) return event.allDayDate;
  if (!event.startAt) return 'unscheduled';
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: event.timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date(event.startAt));
  const y = parts.find((p) => p.type === 'year')?.value;
  const m = parts.find((p) => p.type === 'month')?.value;
  const d = parts.find((p) => p.type === 'day')?.value;
  return `${y}-${m}-${d}`;
}
