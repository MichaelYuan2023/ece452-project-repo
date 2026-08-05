package com.example.houseflow.util

// A minimal, dependency-free iCalendar (.ics) parser for HF-11 school-calendar
// import. It returns structured date/time *components* rather than epoch
// timestamps so it stays fully deterministic and unit-testable — the timezone /
// Calendar conversion happens later, in the mapper (AppViewModel).
//
// Known limitations (documented, acceptable for class schedules entered in local
// time): timezones are treated as floating/local (TZID and trailing 'Z' are read
// as wall-clock, not converted); only weekly RRULEs are recognized as recurring.
object IcsParser {

    data class IcsDateTime(
        val year: Int,
        val month: Int,   // 1-12
        val day: Int,
        val hour: Int,
        val minute: Int,
        val dateOnly: Boolean
    )

    data class IcsEvent(
        val uid: String?,
        val summary: String,
        val start: IcsDateTime?,
        val end: IcsDateTime?,
        val weekly: Boolean   // has an RRULE with FREQ=WEEKLY
    )

    fun parse(text: String): List<IcsEvent> {
        val lines = unfold(text)
        val events = mutableListOf<IcsEvent>()

        var inEvent = false
        var uid: String? = null
        var summary = ""
        var start: IcsDateTime? = null
        var end: IcsDateTime? = null
        var weekly = false

        for (line in lines) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> {
                    inEvent = true; uid = null; summary = ""; start = null; end = null; weekly = false
                }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent) events.add(IcsEvent(uid, summary, start, end, weekly))
                    inEvent = false
                }
                inEvent -> {
                    val (name, params, value) = splitProperty(line) ?: continue
                    when (name.uppercase()) {
                        "UID" -> uid = value
                        "SUMMARY" -> summary = unescape(value)
                        "DTSTART" -> start = parseDateTime(value, params)
                        "DTEND" -> end = parseDateTime(value, params)
                        "RRULE" -> if (value.uppercase().contains("FREQ=WEEKLY")) weekly = true
                    }
                }
            }
        }
        return events
    }

    // Reassembles RFC 5545 folded lines: a line beginning with a space or tab is
    // a continuation of the previous line.
    private fun unfold(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val out = mutableListOf<String>()
        for (line in raw) {
            if (line.isEmpty()) continue
            if ((line[0] == ' ' || line[0] == '\t') && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + line.substring(1)
            } else {
                out.add(line)
            }
        }
        return out
    }

    // Splits "NAME;PARAM=X;PARAM2=Y:VALUE" into (NAME, params, VALUE).
    private fun splitProperty(line: String): Triple<String, String, String>? {
        val colon = line.indexOf(':')
        if (colon < 0) return null
        val left = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val semi = left.indexOf(';')
        return if (semi < 0) Triple(left, "", value)
        else Triple(left.substring(0, semi), left.substring(semi + 1), value)
    }

    // Parses DTSTART/DTEND values: "YYYYMMDDTHHMMSS" (optionally trailing Z),
    // or "YYYYMMDD" (date-only, when VALUE=DATE or an 8-char value).
    private fun parseDateTime(rawValue: String, params: String): IcsDateTime? {
        val value = rawValue.trim().removeSuffix("Z")
        val dateOnly = params.uppercase().contains("VALUE=DATE") || !value.contains("T")
        val datePart = value.substringBefore("T")
        if (datePart.length < 8 || !datePart.substring(0, 8).all { it.isDigit() }) return null
        val year = datePart.substring(0, 4).toInt()
        val month = datePart.substring(4, 6).toInt()
        val day = datePart.substring(6, 8).toInt()
        var hour = 0
        var minute = 0
        if (!dateOnly && value.contains("T")) {
            val timePart = value.substringAfter("T")
            if (timePart.length >= 4 && timePart.substring(0, 4).all { it.isDigit() }) {
                hour = timePart.substring(0, 2).toInt()
                minute = timePart.substring(2, 4).toInt()
            }
        }
        return IcsDateTime(year, month, day, hour, minute, dateOnly)
    }

    private fun unescape(s: String): String =
        s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\N", " ").trim()
}
