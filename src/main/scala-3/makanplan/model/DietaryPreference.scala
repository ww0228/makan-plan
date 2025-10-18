package makanplan.model

// Single user preference entry (e.g., "Vegetarian", "Halal")
final case class DietaryPreference(
                                    id: Long,        // DB id
                                    userId: Long,    // owner
                                    preference: String // preference label
                                  )

// Helpers for creating records
object DietaryPreference {
  // convenience factory for a new preference for a user (id = 0 until persisted)
  def forUser(userId: Long, pref: String): DietaryPreference =
    DietaryPreference(0L, userId, pref)
}
