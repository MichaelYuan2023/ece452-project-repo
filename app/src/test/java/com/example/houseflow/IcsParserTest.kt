package com.example.houseflow

import com.example.houseflow.util.IcsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsParserTest {

    @Test
    fun `parses a single timed event`() {
        val ics = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:abc-123
            SUMMARY:Math 101
            DTSTART:20260812T140000
            DTEND:20260812T153000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = IcsParser.parse(ics)
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("abc-123", e.uid)
        assertEquals("Math 101", e.summary)
        assertFalse(e.weekly)
        assertEquals(2026, e.start!!.year)
        assertEquals(8, e.start!!.month)
        assertEquals(12, e.start!!.day)
        assertEquals(14, e.start!!.hour)
        assertEquals(0, e.start!!.minute)
        assertFalse(e.start!!.dateOnly)
        assertEquals(15, e.end!!.hour)
        assertEquals(30, e.end!!.minute)
    }

    @Test
    fun `reassembles folded summary lines`() {
        val ics = "BEGIN:VEVENT\r\nUID:x\r\nSUMMARY:Intro to \r\n Computer Science\r\nDTSTART:20260101T090000\r\nEND:VEVENT"
        val events = IcsParser.parse(ics)
        assertEquals("Intro to Computer Science", events[0].summary)
    }

    @Test
    fun `detects weekly recurrence`() {
        val ics = """
            BEGIN:VEVENT
            UID:weekly-1
            SUMMARY:Lecture
            DTSTART:20260901T100000
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR;UNTIL=20261215T000000Z
            END:VEVENT
        """.trimIndent()
        val e = IcsParser.parse(ics).single()
        assertTrue(e.weekly)
    }

    @Test
    fun `parses all-day event as date-only`() {
        val ics = """
            BEGIN:VEVENT
            UID:allday
            SUMMARY:Reading Week
            DTSTART;VALUE=DATE:20261019
            DTEND;VALUE=DATE:20261024
            END:VEVENT
        """.trimIndent()
        val e = IcsParser.parse(ics).single()
        assertTrue(e.start!!.dateOnly)
        assertEquals(2026, e.start!!.year)
        assertEquals(10, e.start!!.month)
        assertEquals(19, e.start!!.day)
    }

    @Test
    fun `parses multiple events and tolerates one without dtstart`() {
        val ics = """
            BEGIN:VEVENT
            UID:1
            SUMMARY:Has start
            DTSTART:20260101T080000
            END:VEVENT
            BEGIN:VEVENT
            UID:2
            SUMMARY:No start
            END:VEVENT
        """.trimIndent()
        val events = IcsParser.parse(ics)
        assertEquals(2, events.size)
        assertEquals(8, events[0].start!!.hour)
        assertNull(events[1].start)
    }

    @Test
    fun `handles UTC suffix as wall-clock`() {
        val ics = "BEGIN:VEVENT\nUID:z\nSUMMARY:UTC\nDTSTART:20260305T233000Z\nEND:VEVENT"
        val e = IcsParser.parse(ics).single()
        assertEquals(23, e.start!!.hour)
        assertEquals(30, e.start!!.minute)
    }
}
