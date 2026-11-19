package com.strongppl.tracker.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class ExercisePlan(
    val id: String, val name: String, val sets: Int, val minReps: Int, val maxReps: Int,
    val focus: String = "", val note: String = ""
)
data class DayPlan(val day: DayOfWeek, val title: String, val focus: String, val exercises: List<ExercisePlan>, val isRest: Boolean = false)

object WorkoutPlan {
    val days = listOf(
        DayPlan(DayOfWeek.MONDAY, "Push A", "Upper chest · side delts · triceps", listOf(
            ex("incline_band_press", "Incline Band Chest Press",3,10,15),
            ex("low_high_fly", "Low-to-High Band Fly",2,12,20),
            ex("band_lateral_a", "Single-Arm Band Lateral Raise",3,12,20),
            ex("close_grip_pushup", "Close-Grip Incline Push-Up",2,8,15),
            ex("overhead_tri", "Overhead Band Triceps Extension",2,10,20,note="Only use a pain-free range; substitute a band pushdown if the shoulder feels pinchy."),
            ex("band_triceps_kickback", "Band Triceps Kickback",2,12,20)
        )),
        DayPlan(DayOfWeek.TUESDAY, "Pull A", "Lat width · back thickness · rear delts · biceps", listOf(
            ex("kneeling_pulldown", "Kneeling Band Lat Pulldown",3,10,15),
            ex("barbell_row", "Barbell Bent-Over Row",3,8,12),
            ex("rear_delt_a", "Single-Arm Band Rear-Delt Fly",2,12,20),
            ex("ez_curl", "EZ-Bar Biceps Curl",3,8,15),
            ex("hammer_curl", "Band Hammer Curl",2,12,20),
            ex("single_arm_band_row", "Single-Arm Band Row",2,10,15)
        )),
        DayPlan(DayOfWeek.WEDNESDAY, "Legs A", "Quads · unilateral strength · calves · forearms · core", listOf(
            ex("reverse_lunge", "Reverse Lunge",3,8,12,note="Start bodyweight; add 5 kg dumbbells only when balance and control are reliable."),
            ex("band_leg_ext", "Band Leg Extension",2,12,20),
            ex("lateral_lunge", "Lateral Lunge",2,8,12),
            ex("standing_calf", "Standing Calf Raise",3,12,20),
            ex("wrist_curl", "Barbell Wrist Curl",2,12,20),
            ex("pallof", "Pallof Press",2,10,15)
        )),
        DayPlan(DayOfWeek.THURSDAY, "Push B", "Chest · side delts · triceps · shoulder control", listOf(
            ex("floor_press", "Barbell Floor Press",3,8,12),
            ex("high_low_fly", "High-to-Low Band Fly",2,12,20),
            ex("lean_lateral", "Leaning Single-Arm Band Lateral Raise",3,12,20),
            ex("pushdown", "Band Triceps Pushdown",2,10,20),
            ex("incline_pushup", "Standard Incline Push-Up",2,8,15),
            ex("band_external_rotation", "Band External Rotation",2,12,20,note="Light, controlled shoulder-conditioning work; stop if painful. Not a substitute for an individual shoulder assessment.")
        )),
        DayPlan(DayOfWeek.FRIDAY, "Pull B", "Upper back · lats · traps · biceps / brachialis", listOf(
            ex("high_row", "Band Wide-Elbow High Row",3,10,15),
            ex("straight_pulldown", "Straight-Arm Band Pulldown",2,12,20),
            ex("face_pull", "Band Face Pull",2,12,20),
            ex("shrug", "Barbell Shrug",2,10,15),
            ex("incline_curl", "Incline Dumbbell Curl",2,10,15,note="Use band curls if 5 kg cannot be lowered under control."),
            ex("reverse_curl", "EZ-Bar Reverse Curl",2,10,15)
        )),
        DayPlan(DayOfWeek.SATURDAY, "Legs B", "Hamstrings · glutes · calves · forearms · core", listOf(
            ex("rdl", "Barbell Romanian Deadlift",3,8,12),
            ex("hip_thrust", "Barbell Hip Thrust",3,10,15),
            ex("band_leg_curl", "Lying Band Leg Curl",2,12,20),
            ex("seated_calf", "Seated Calf Raise",2,12,20),
            ex("reverse_wrist", "Reverse Wrist Curl",2,12,20),
            ex("dead_bug", "Dead Bug",2,8,12)
        )),
        DayPlan(DayOfWeek.SUNDAY, "Rest & Recovery", "Recovery day", emptyList(), true)
    )
    fun forDate(date: LocalDate): DayPlan = days.first { it.day == date.dayOfWeek }
    fun programmeWeek(date: LocalDate, anchorMonday: LocalDate): Int =
        (((date.toEpochDay() - anchorMonday.toEpochDay()) / 7).toInt() + 1).coerceIn(1, 12)
    fun currentWeekMonday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private fun ex(id:String,name:String,sets:Int,min:Int,max:Int,note:String="") = ExercisePlan(id,name,sets,min,max,note=note)
}
