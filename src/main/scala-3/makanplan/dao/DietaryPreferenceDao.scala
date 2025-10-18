package makanplan.dao

import scalikejdbc._
import makanplan.model.DietaryPreference

object DietaryPreferenceDao:

  // Find dietary preference by userId
  def findByUser(userId: Long)(using DBSession): Option[DietaryPreference] =
    sql"select id, user_id, preference from dietary_preferences where user_id = $userId"
      .map(rs => DietaryPreference(rs.long("id"), rs.long("user_id"), rs.string("preference")))
      .single.apply()

  // Insert or update dietary preference for a user
  def upsert(dietaryPreference: DietaryPreference)(using DBSession): Unit =
    sql"""
      merge into dietary_preferences (user_id, preference)
      key (user_id)
      values (${dietaryPreference.userId}, ${dietaryPreference.preference})
    """.update.apply()
