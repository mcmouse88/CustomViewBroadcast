package com.mcmouse88.ganttchart

import java.time.LocalDate

data class Task(
    val name: String,
    val dateStart: LocalDate,
    val dateEnd: LocalDate
)
