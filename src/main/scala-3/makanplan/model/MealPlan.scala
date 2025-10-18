package makanplan.model

import java.time.LocalDate

// Weekly meal plan window for a user
final case class MealPlan(
  id: Long,              // DB id
  userId: Long,          // owner
  startDate: LocalDate,  // week start (inclusive)
  endDate: LocalDate     // week end (inclusive)
)
