package makanplan.model

// Per-user app defaults/settings
final case class UserPreferences(
  userId: Long,        // owner user (FK)
  defaultMode: String, // e.g., "Light"/"Dark" or other UI mode
  defaultBudget: Double// monthly/weekly budget default (currency units)
)
