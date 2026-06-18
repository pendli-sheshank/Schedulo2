package com.example

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.core.content.edit

data class CalendarInfo(
    val id: Long,
    val name: String,
    val color: Int
)

object CalendarService {

    private const val PREFS_NAME = "calendar_sync_prefs"
    private const val KEY_SYNC_ENABLED = "calendar_sync_enabled"
    private const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"
    private const val KEY_EVENT_MAP_PREFIX = "event_map_"

    fun isCalendarSyncEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SYNC_ENABLED, false)
    }

    fun setCalendarSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_SYNC_ENABLED, enabled)
        }
    }

    fun getSelectedCalendarId(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_SELECTED_CALENDAR_ID, -1L)
    }

    fun setSelectedCalendarId(context: Context, calendarId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putLong(KEY_SELECTED_CALENDAR_ID, calendarId)
        }
    }

    fun getWritableCalendars(context: Context): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR
            )
            val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
            val selectionArgs = arrayOf("${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}")

            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val colorIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                while (cursor.moveToNext()) {
                    calendars.add(
                        CalendarInfo(
                            id = cursor.getLong(idIdx),
                            name = cursor.getString(nameIdx) ?: "Unknown",
                            color = cursor.getInt(colorIdx)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            // Other errors
        }
        return calendars
    }

    fun syncShiftToCalendar(context: Context, shift: Shift) {
        if (!isCalendarSyncEnabled(context)) return
        val calendarId = getSelectedCalendarId(context)
        if (calendarId == -1L) return

        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existingEventId = prefs.getLong("${KEY_EVENT_MAP_PREFIX}${shift.id}", -1L)

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, "${shift.company} - ${shift.role}".let {
                    if (shift.role.isBlank()) shift.company else it
                })
                put(CalendarContract.Events.DTSTART, shift.startTime)
                put(CalendarContract.Events.DTEND, shift.endTime)
                put(CalendarContract.Events.DESCRIPTION, shift.notes)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }

            if (existingEventId != -1L) {
                // Update existing event
                val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
                val rowsUpdated = context.contentResolver.update(updateUri, values, null, null)
                if (rowsUpdated == 0) {
                    // Event was deleted externally, create new one
                    insertNewEvent(context, values, shift.id)
                }
            } else {
                insertNewEvent(context, values, shift.id)
            }
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            // Calendar operation failed — do not block shift operations
        }
    }

    private fun insertNewEvent(context: Context, values: ContentValues, shiftId: String) {
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        if (uri != null) {
            val eventId = ContentUris.parseId(uri)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putLong("${KEY_EVENT_MAP_PREFIX}$shiftId", eventId)
            }
        }
    }

    fun removeShiftFromCalendar(context: Context, shiftId: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val eventId = prefs.getLong("${KEY_EVENT_MAP_PREFIX}$shiftId", -1L)
            if (eventId != -1L) {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                context.contentResolver.delete(deleteUri, null, null)
                prefs.edit {
                    remove("${KEY_EVENT_MAP_PREFIX}$shiftId")
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
        } catch (e: Exception) {
            // Calendar operation failed
        }
    }

    fun syncAllShifts(context: Context, shifts: List<Shift>) {
        if (!isCalendarSyncEnabled(context)) return
        for (shift in shifts) {
            try {
                syncShiftToCalendar(context, shift)
            } catch (e: Exception) {
                // Continue syncing other shifts
            }
        }
    }

    fun removeAllSyncedEvents(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val allEntries = prefs.all
            val eventKeys = allEntries.keys.filter { it.startsWith(KEY_EVENT_MAP_PREFIX) }
            for (key in eventKeys) {
                val eventId = prefs.getLong(key, -1L)
                if (eventId != -1L) {
                    try {
                        val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                        context.contentResolver.delete(deleteUri, null, null)
                    } catch (e: Exception) {
                        // Continue removing other events
                    }
                }
            }
            prefs.edit {
                eventKeys.forEach { remove(it) }
            }
        } catch (e: Exception) {
            // Cleanup failed
        }
    }
}
