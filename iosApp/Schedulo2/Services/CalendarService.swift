import Foundation
import EventKit

final class CalendarService: ObservableObject {
    static let shared = CalendarService()

    private let eventStore = EKEventStore()
    private let defaults = UserDefaults.standard

    private let syncEnabledKey = "calendarSyncEnabled"
    private let selectedCalendarKey = "selectedCalendarIdentifier"
    private let eventMapKey = "calendarEventMap"

    @Published var calendarSyncEnabled: Bool {
        didSet { defaults.set(calendarSyncEnabled, forKey: syncEnabledKey) }
    }

    @Published var selectedCalendarIdentifier: String? {
        didSet { defaults.set(selectedCalendarIdentifier, forKey: selectedCalendarKey) }
    }

    private init() {
        self.calendarSyncEnabled = defaults.bool(forKey: syncEnabledKey)
        self.selectedCalendarIdentifier = defaults.string(forKey: selectedCalendarKey)
    }

    // MARK: - Access

    func requestAccess() async -> Bool {
        if #available(iOS 17.0, *) {
            do {
                return try await eventStore.requestFullAccessToEvents()
            } catch {
                print("CalendarService: Failed to request full access: \(error.localizedDescription)")
                return false
            }
        } else {
            do {
                return try await eventStore.requestAccess(to: .event)
            } catch {
                print("CalendarService: Failed to request access: \(error.localizedDescription)")
                return false
            }
        }
    }

    // MARK: - Calendars

    func getAvailableCalendars() -> [EKCalendar] {
        eventStore.calendars(for: .event).filter { $0.allowsContentModifications }
    }

    private var targetCalendar: EKCalendar? {
        if let identifier = selectedCalendarIdentifier,
           let calendar = eventStore.calendar(withIdentifier: identifier),
           calendar.allowsContentModifications {
            return calendar
        }
        return eventStore.defaultCalendarForNewEvents
    }

    // MARK: - Event Map

    private var eventMap: [String: String] {
        get { defaults.dictionary(forKey: eventMapKey) as? [String: String] ?? [:] }
        set { defaults.set(newValue, forKey: eventMapKey) }
    }

    // MARK: - Sync

    func syncShiftToCalendar(shift: Shift) {
        guard calendarSyncEnabled else { return }
        guard let calendar = targetCalendar else {
            print("CalendarService: No writable calendar available")
            return
        }

        var map = eventMap
        let event: EKEvent

        // Check if we already have an event for this shift
        if let existingId = map[shift.id],
           let existingEvent = eventStore.event(withIdentifier: existingId) {
            event = existingEvent
        } else {
            event = EKEvent(eventStore: eventStore)
        }

        let title: String
        if !shift.company.isEmpty && !shift.role.isEmpty {
            title = "\(shift.company) - \(shift.role)"
        } else if !shift.company.isEmpty {
            title = shift.company
        } else {
            title = "Shift"
        }

        event.title = title
        event.startDate = Date(timeIntervalSince1970: Double(shift.startTime) / 1000.0)
        event.endDate = Date(timeIntervalSince1970: Double(shift.endTime) / 1000.0)
        event.notes = shift.notes.isEmpty ? nil : shift.notes
        event.calendar = calendar

        do {
            try eventStore.save(event, span: .thisEvent)
            map[shift.id] = event.eventIdentifier
            eventMap = map
        } catch {
            print("CalendarService: Failed to save event: \(error.localizedDescription)")
        }
    }

    func removeShiftFromCalendar(shiftId: String) {
        var map = eventMap
        guard let eventId = map[shiftId],
              let event = eventStore.event(withIdentifier: eventId) else {
            map.removeValue(forKey: shiftId)
            eventMap = map
            return
        }

        do {
            try eventStore.remove(event, span: .thisEvent)
            map.removeValue(forKey: shiftId)
            eventMap = map
        } catch {
            print("CalendarService: Failed to remove event: \(error.localizedDescription)")
        }
    }

    func syncAllShifts(shifts: [Shift]) {
        guard calendarSyncEnabled else { return }
        for shift in shifts {
            syncShiftToCalendar(shift: shift)
        }
    }

    func removeAllSyncedEvents() {
        let map = eventMap
        for (_, eventId) in map {
            if let event = eventStore.event(withIdentifier: eventId) {
                do {
                    try eventStore.remove(event, span: .thisEvent)
                } catch {
                    print("CalendarService: Failed to remove event: \(error.localizedDescription)")
                }
            }
        }
        eventMap = [:]
    }
}
