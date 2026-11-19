package com.strongppl.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WorkoutPlanTest {
    @Test fun everyTrainingDayHasSixExercisesAndSundayIsRest() {
        WorkoutPlan.days.filterNot { it.isRest }.forEach { assertEquals(6, it.exercises.size) }
        assertTrue(WorkoutPlan.forDate(LocalDate.of(2026, 11, 22)).isRest)
    }
    @Test fun noExerciseIdsRepeatAcrossTheWeek() {
        val ids = WorkoutPlan.days.flatMap { it.exercises }.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
    @Test fun weekdayMappingDoesNotDependOnInstallDate() {
        assertEquals("Push A", WorkoutPlan.forDate(LocalDate.of(2026, 11, 23)).title)
        assertEquals("Pull A", WorkoutPlan.forDate(LocalDate.of(2026, 11, 24)).title)
        assertEquals("Legs B", WorkoutPlan.forDate(LocalDate.of(2026, 11, 28)).title)
        assertEquals(DayOfWeek.SUNDAY, WorkoutPlan.forDate(LocalDate.of(2026, 11, 22)).day)
    }
    @Test fun everyProgrammedExerciseHasAnInstructionGuide() {
        WorkoutPlan.days.flatMap { it.exercises }.forEach {
            assertTrue("Missing guide for ${it.id}", ExerciseEvidence.guide(it.id) != null)
        }
    }
}
