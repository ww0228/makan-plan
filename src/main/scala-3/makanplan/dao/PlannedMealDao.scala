package makanplan.dao

import scalikejdbc.*
import makanplan.model.PlannedMeal

object PlannedMealDao extends CrudDao[PlannedMeal, Long]:
  // CRUD for meal_plan_meals table

  // map a DB row to PlannedMeal
  private def map(rs: WrappedResultSet): PlannedMeal =
    PlannedMeal(
      id         = rs.long("id"),
      mealPlanId = rs.long("meal_plan_id"),
      name       = rs.string("name"),
      kind       = rs.string("kind"),
      servings   = rs.int("servings"),
      cost       = rs.double("cost")
    )

  // find one by id
  def findById(id: Long)(using DBSession): Option[PlannedMeal] =
    sql"select * from meal_plan_meals where id = $id".map(map).single.apply()

  // all meals for a given plan (in insert order)
  def findByPlan(planId: Long)(using DBSession): List[PlannedMeal] =
    sql"select * from meal_plan_meals where meal_plan_id = $planId order by id asc"
      .map(map).list.apply()

  // insert and return generated id
  def create(t: PlannedMeal)(using DBSession): Long =
    sql"""
      insert into meal_plan_meals (meal_plan_id, name, kind, servings, cost)
      values (${t.mealPlanId}, ${t.name}, ${t.kind}, ${t.servings}, ${t.cost})
    """.updateAndReturnGeneratedKey.apply()

  // update by id scoped to its plan
  def update(t: PlannedMeal)(using DBSession): Int =
    sql"""
      update meal_plan_meals
      set name = ${t.name}, kind = ${t.kind}, servings = ${t.servings}, cost = ${t.cost}
      where id = ${t.id} and meal_plan_id = ${t.mealPlanId}
    """.update.apply()

  // delete one by id
  def delete(id: Long)(using DBSession): Int =
    sql"delete from meal_plan_meals where id = $id".update.apply()

  // delete all meals under a plan (used for "Clear week")
  def deleteByPlan(planId: Long)(using DBSession): Int =
    sql"delete from meal_plan_meals where meal_plan_id = $planId".update.apply()
