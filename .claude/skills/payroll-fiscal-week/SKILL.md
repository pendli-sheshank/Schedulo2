---
name: payroll-fiscal-week
description: Fiscal-week grouping rules for all payroll, earnings, weekly-summary, pay-cycle, and widget code in Schedulo2. Use whenever adding or changing any code that groups shifts or earnings by week — payroll cycles, weekly insights, dashboard week cards, widget totals, exports — on Android, iOS, or the shared KMP module.
---

# Payroll Fiscal-Week Grouping

## The rule

Payroll weeks are **per-job fiscal weeks** anchored to the job's `weeklyCycleStartDay`
field (Firestore `jobs` collection, default `"Monday"`). A job set to `"Friday"` pays in
Friday → Thursday cycles: all seven days from Friday through the following Thursday form
ONE "Weekly Payroll Cycle".

**Never hardcode Monday/Sunday or use locale calendar weeks in payroll or earnings code.**
Forbidden patterns in any weekly grouping of shifts/earnings:

- Android/Kotlin: `Calendar.MONDAY` anchors, `firstDayOfWeek = Calendar.MONDAY`, `DayOfWeek.MONDAY` walk-backs
- iOS/Swift: `comps.weekday = 2`, `calendar.firstWeekday`, `dateInterval(of: .weekOfYear)`

This mistake has shipped before (weekly summaries split a Friday–Thursday pay period at
the Sunday/Monday boundary) and must not be reintroduced.

The only intentional exception: the Plan tab's **calendar display grids**
(Android `CalendarWeekView`/`CalendarMonthView` in `TabsSupport.kt`, iOS `PlanView.swift`)
are Monday-first schedule calendars, not payroll groupings. Leave them calendar-based.

## Which day to use

1. **Single-employer context** (a specific job's pay cycles, employer-filtered insights):
   that job's `weeklyCycleStartDay`. Match job to shift by `title == shift.company`,
   case-insensitive. Fallback `"Monday"`.
2. **Cross-job aggregate views** (dashboard week card, widgets, unfiltered insights,
   week pickers): the most common `weeklyCycleStartDay` among **non-gig** jobs; ties
   broken by earliest day (Sunday first); `"Monday"` when there are no non-gig jobs.
   This is deterministic regardless of Firestore result order.

## Canonical helpers — reuse these, never write new week-boundary date math

| Platform | Helpers | File |
|---|---|---|
| Shared KMP | `Job.getStartOfCurrentCycle()`, `weekStartDayOfWeek()`, `resolveGlobalWeekStartDay()` | `shared/src/commonMain/kotlin/com/schedulo/shared/model/Job.kt` |
| Shared KMP | `getWeeklyEarningsSummary(weekStartDay = …)` | `shared/src/commonMain/kotlin/com/schedulo/shared/logic/InsightsCalculator.kt` |
| Android | `startOfWeekContaining()`, `weekStartCalendarDay()`, `Job.getStartOfCurrentCycle()` | `androidApp/src/main/java/com/example/DashboardSupport.kt` |
| Android | `DashboardViewModel.resolveGlobalWeekStartDay()` | `androidApp/src/main/java/com/example/DashboardSupport.kt` |
| iOS | `DashboardViewModel.startOfWeek(containing:weekStartDay:)`, `DashboardViewModel.resolveGlobalWeekStartDay()` | `iosApp/Schedulo2/ViewModels/DashboardViewModel.swift` |
| iOS | `Job.getStartOfCurrentCycle(targetDate:)` | `iosApp/Schedulo2/Services/FirebaseService.swift` |

Week math is always: start-of-day, walk back one day at a time until the weekday equals
the target start day; week end = start + 7 days.

## cycleKey warning (persisted data)

Pay adjustments in Firestore `pay_adjustments` carry a `cycleKey` derived from the cycle
start date. The formats differ per platform and must each stay byte-stable:

- Android: `"${employer}_${cycleStartEpochMillis}"` (`WeeklyPayCycle` in `TabsSupport.kt`)
- iOS: `employer + "yyyy-MM-dd"` with **no delimiter** (`PayCycleInfo` in `PayView.swift`)

Changing how a cycle start is computed silently orphans previously saved adjustments
(they stop matching any cycle). Stale keys from before the fiscal-week fix are
intentionally **not** migrated. Do not "unify" or reformat cycleKey without a migration.

## Checklist for any change touching weekly grouping

1. Update shared KMP model/logic if affected (`shared/src/commonMain/`).
2. Update BOTH Android and iOS (ViewModels + views + widgets).
3. Add or update shared tests (`shared/src/commonTest/.../InsightsCalculatorTest.kt`);
   run with `./gradlew :shared:testAndroidHostTest`.
4. Bump the fallback version code in `androidApp/build.gradle.kts`.
5. Re-grep for forbidden patterns before committing:
   `Calendar.MONDAY`, `DayOfWeek.MONDAY`, `weekday = 2`, `dateInterval(of: .weekOfYear`.
