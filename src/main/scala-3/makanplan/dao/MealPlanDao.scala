package makanplan.dao

import scalikejdbc.*
import makanplan.model.MealPlan
import java.time.{LocalDate, DayOfWeek}

object MealPlanDao extends CrudDao[MealPlan, Long]:
  // CRUD for meal_plans table

  // map a DB row to MealPlan
  private def map(rs: WrappedResultSet): MealPlan =
    MealPlan(
      id        = rs.long("id"),
      userId    = rs.long("user_id"),
      startDate = rs.localDate("start_date"),
      endDate   = rs.localDate("end_date")
    )

  // find one by id
  def findById(id: Long)(using DBSession): Option[MealPlan] =
    sql"select * from meal_plans where id = $id".map(map).single.apply()

  // plan that covers "today" for a user (if any)
  def findActiveForUser(userId: Long)(using DBSession): Option[MealPlan] =
    val today = LocalDate.now
    sql"""
      select * from meal_plans
      where user_id = $userId and start_date <= $today and end_date >= $today
      order by start_date desc
      limit 1
    """.map(map).single.apply()

  // insert a plan for a given week (inclusive)
  def createForWeek(userId: Long, start: LocalDate, end: LocalDate)(using DBSession): Long =
    sql"""
      insert into meal_plans (user_id, start_date, end_date)
      values ($userId, $start, $end)
    """.updateAndReturnGeneratedKey.apply()

  // compute current week's Monday..Sunday
  def currentWeekRange(): (LocalDate, LocalDate) =
    val today = LocalDate.now
    val monday = today.`with`(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    (monday, sunday)

  // create via createForWeek (CrudDao create)
  def create(t: MealPlan)(using DBSession): Long =
    createForWeek(t.userId, t.startDate, t.endDate)

  // update by id scoped to userId
  def update(t: MealPlan)(using DBSession): Int =
    sql"""
      update meal_plans
      set start_date = ${t.startDate}, end_date = ${t.endDate}
      where id = ${t.id} and user_id = ${t.userId}
    """.update.apply()

  // delete by id
  def delete(id: Long)(using DBSession): Int =
    sql"delete from meal_plans where id = $id".update.apply()
