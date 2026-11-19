package com.strongppl.tracker.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutPlanTest {
    @Test fun weekdayScheduleNeverDependsOnInstallDay() {
        assertEquals("Push A", WorkoutPlan.forDate(LocalDate.parse("2026-11-16")).title)
        assertEquals("Pull A", WorkoutPlan.forDate(LocalDate.parse("2026-11-17")).title)
        assertEquals("Legs A", WorkoutPlan.forDate(LocalDate.parse("2026-11-18")).title)
        assertEquals("Push B", WorkoutPlan.forDate(LocalDate.parse("2026-11-19")).title)
        assertEquals("Pull B", WorkoutPlan.forDate(LocalDate.parse("2026-11-20")).title)
        assertEquals("Legs B", WorkoutPlan.forDate(LocalDate.parse("2026-11-21")).title)
        assertTrue(WorkoutPlan.forDate(LocalDate.parse("2026-11-22")).isRest)
    }
    @Test fun weekStartsOnMonday() {
        val anchor=LocalDate.parse("2026-11-16")
        assertEquals(1,WorkoutPlan.programmeWeek(LocalDate.parse("2026-11-22"),anchor))
        assertEquals(2,WorkoutPlan.programmeWeek(LocalDate.parse("2026-11-23"),anchor))
    }
}
