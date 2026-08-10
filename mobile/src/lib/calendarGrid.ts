export interface CalendarCell {
  date: Date;
  isCurrentMonth: boolean;
  isoDate: string;
}

/** Real date-grid math (not a static mockup grid) — a 6-row, Sun-first month grid, including the trailing/leading days of adjacent months so every week row is full. */
export function buildMonthGrid(year: number, month0: number): CalendarCell[] {
  const firstOfMonth = new Date(year, month0, 1);
  const startOffset = firstOfMonth.getDay();
  const gridStart = new Date(year, month0, 1 - startOffset);

  return Array.from({ length: 42 }, (_, i) => {
    const date = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i);
    return {
      date,
      isCurrentMonth: date.getMonth() === month0,
      isoDate: toIsoDate(date),
    };
  });
}

export function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

export function formatMonthLabel(year: number, month0: number): string {
  return new Date(year, month0, 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}

export function formatWeekdayHeader(dayIndex: number): string {
  return ['S', 'M', 'T', 'W', 'T', 'F', 'S'][dayIndex];
}

export function formatAgendaDateHeader(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric' });
}
