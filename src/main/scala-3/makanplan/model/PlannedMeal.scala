package makanplan.model

// A single planned meal entry under a MealPlan
final case class PlannedMeal(
  id: Long,          // DB id
  mealPlanId: Long,  // parent MealPlan (FK)
  name: String,      // meal name (may include day tag like "Nasi Lemak [2025-08-22]")
  kind: String,      // type: Breakfast / Lunch / Dinner / Snack / etc.
  servings: Int,     // number of servings
  cost: Double       // total cost for this meal
)
