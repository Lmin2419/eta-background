// Ported from app/src/main/kotlin/io/github/mangi/eta/ui/app/ConversationTimeLabels.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Java Calendar/DateFormat are isolated behind a locale port; no Node Intl in production core.
export interface CalendarDate { year: number; month: number; day: number; }
export interface ConversationDateFormat {
  calendarDate(timestampMillis: number, timeZone: string): CalendarDate;
  format(timestampMillis: number, pattern: string, locale: string, timeZone: string): string;
  mediumDate(timestampMillis: number, locale: string, timeZone: string): string;
}
export class ConversationTimeLabels {
  static label(timestampMillis: number, nowMillis: number, locale: string, timeZone: string,
    formatter: ConversationDateFormat, use24HourClock: boolean = true, yesterdayLabel: string = 'Yesterday', recentLabel: string = 'Recent'): string {
    if (timestampMillis <= 0) return recentLabel;
    const now = formatter.calendarDate(nowMillis, timeZone), target = formatter.calendarDate(timestampMillis, timeZone);
    const days = Math.round((Date.UTC(now.year, now.month - 1, now.day) - Date.UTC(target.year, target.month - 1, target.day)) / 86400000);
    if (days <= 0) return formatter.format(timestampMillis, use24HourClock ? 'HH:mm' : 'h:mm a', locale, timeZone);
    if (days === 1) return yesterdayLabel;
    if (days <= 6) return formatter.format(timestampMillis, 'EEE', locale, timeZone);
    if (now.year === target.year) {
      const language = locale.split(/[-_]/)[0];
      const pattern = language === 'zh' || language === 'ja' ? 'M月d日' : language === 'ko' ? 'M월 d일' : 'MMM d';
      return formatter.format(timestampMillis, pattern, locale, timeZone);
    }
    return formatter.mediumDate(timestampMillis, locale, timeZone);
  }
}
