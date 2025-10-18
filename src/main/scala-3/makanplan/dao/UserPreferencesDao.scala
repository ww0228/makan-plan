package makanplan.dao

import scalikejdbc._
import makanplan.model.UserPreferences

object UserPreferencesDao:

  // Find settings by userId
  def findByUser(userId: Long)(using DBSession): Option[UserPreferences] =
    sql"select user_id, default_mode, default_budget from settings where user_id = $userId"
      .map(rs => UserPreferences(rs.long("user_id"), rs.string("default_mode"), rs.double("default_budget")))
      .single.apply()

  // Insert or update settings
  def upsert(settings: UserPreferences)(using DBSession): Unit =
    sql"""
      merge into settings (user_id, default_mode, default_budget)
      key (user_id)
      values (${settings.userId}, ${settings.defaultMode}, ${settings.defaultBudget})
    """.update.apply()
