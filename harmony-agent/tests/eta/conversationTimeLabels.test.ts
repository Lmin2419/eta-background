// Ported from app/src/test/kotlin/io/github/mangi/eta/ui/app/ConversationTimeLabelsTest.kt @ 3b1794d.
// Required Notice: Copyright © 2026 蛮吉 (Mangi-11).
// Node Intl implements the Java locale dependency for tests only; device adapter belongs to E07.
import test from 'node:test';
import assert from 'node:assert/strict';
import { ConversationTimeLabels as T, ConversationDateFormat, CalendarDate } from '../../entry/src/main/ets/core/eta/conversationTimeLabels';
class TestDateFormat implements ConversationDateFormat {
  calendarDate(t: number, timeZone: string): CalendarDate {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone, year: 'numeric', month: 'numeric', day: 'numeric' }).formatToParts(t);
    const get = (name: string) => Number(parts.find(x => x.type === name)!.value);
    return { year: get('year'), month: get('month'), day: get('day') };
  }
  format(t: number, pattern: string, locale: string, timeZone: string): string {
    if (pattern === 'HH:mm' || pattern === 'h:mm a') return new Intl.DateTimeFormat(locale, { timeZone, hour: pattern === 'HH:mm' ? '2-digit' : 'numeric', minute: '2-digit', hourCycle: pattern === 'HH:mm' ? 'h23' : 'h12' }).format(t);
    if (pattern === 'EEE') return new Intl.DateTimeFormat(locale, { timeZone, weekday: 'short' }).format(t);
    const day = this.calendarDate(t, timeZone);
    if (pattern === 'M月d日') return `${day.month}月${day.day}日`;
    if (pattern === 'M월 d일') return `${day.month}월 ${day.day}일`;
    return new Intl.DateTimeFormat(locale, { timeZone, month: pattern === 'MMM d' ? 'short' : 'numeric', day: 'numeric' }).format(t);
  }
  mediumDate(t: number, locale: string, timeZone: string): string { return new Intl.DateTimeFormat(locale, { timeZone, dateStyle: 'medium' }).format(t); }
}
const formatter = new TestDateFormat(), zone = 'Asia/Shanghai';
const millis = (y: number, m: number, d: number, h: number, min: number) => Date.UTC(y, m - 1, d, h - 8, min);
const now = millis(2026, 7, 4, 19, 32);
const label = (timestamp: number) => T.label(timestamp, now, 'zh-CN', zone, formatter, true, '昨天', '最近');
test('labelUsesClockTimeForToday', () => { assert.equal(label(millis(2026, 7, 4, 9, 5)), '09:05'); });
test('labelUsesRelativeDayForRecentHistory', () => { assert.equal(label(millis(2026, 7, 3, 23, 59)), '昨天'); assert.equal(label(millis(2026, 6, 29, 8, 0)), '周一'); });
test('labelUsesDateForOlderHistory', () => { assert.equal(label(millis(2026, 6, 20, 8, 0)), '6月20日'); assert.equal(label(millis(2025, 12, 31, 8, 0)), '2025年12月31日'); });
test('labelFallsBackForInvalidTimestamp', () => { assert.equal(label(0), '最近'); });
test('labelSupportsEnglishAndTwelveHourClock', () => {
  assert.equal(T.label(millis(2026, 7, 4, 9, 5), now, 'en-US', zone, formatter, false), '9:05 AM');
  assert.equal(T.label(millis(2026, 7, 3, 23, 59), now, 'en-US', zone, formatter, false), 'Yesterday');
});
test('relativeDaysRemainCorrectAcrossDaylightSavingChanges', () => {
  const next = Date.parse('2026-03-09T00:30:00-07:00'), previous = Date.parse('2026-03-08T00:30:00-08:00');
  assert.equal(T.label(previous, next, 'en-US', 'America/Los_Angeles', formatter), 'Yesterday');
});
