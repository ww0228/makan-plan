package makanplan.dao

import scalikejdbc.*
import makanplan.model.RecipeIngredient

object RecipeIngredientDao:

  // map a DB row to RecipeIngredient
  private def from(rs: WrappedResultSet): RecipeIngredient =
    RecipeIngredient(
      id         = rs.long("id"),
      recipeId   = rs.long("recipe_id"),
      foodItemId = rs.long("food_item_id"),
      quantity   = rs.double("quantity"),
      unit       = rs.string("unit")
    )

  // insert and return generated id
  def insert(ri: RecipeIngredient)(using DBSession): Long =
    sql"""
      INSERT INTO recipe_ingredients (recipe_id, food_item_id, quantity, unit)
      VALUES (${ri.recipeId}, ${ri.foodItemId}, ${ri.quantity}, ${ri.unit})
    """.updateAndReturnGeneratedKey.apply()

  // upsert: insert if id == 0, else update; returns id
  def upsert(ri: RecipeIngredient)(using DBSession): Long =
    if ri.id == 0 then insert(ri)
    else
      sql"""
        UPDATE recipe_ingredients
        SET recipe_id = ${ri.recipeId},
            food_item_id = ${ri.foodItemId},
            quantity = ${ri.quantity},
            unit = ${ri.unit}
        WHERE id = ${ri.id}
      """.update.apply()
      ri.id

  // find one by id
  def findById(id: Long)(using DBSession): Option[RecipeIngredient] =
    sql"SELECT * FROM recipe_ingredients WHERE id = $id"
      .map(from).single.apply()

  // all ingredients for a recipe
  def findByRecipe(recipeId: Long)(using DBSession): List[RecipeIngredient] =
    sql"SELECT * FROM recipe_ingredients WHERE recipe_id = $recipeId ORDER BY id"
      .map(from).list.apply()

  // delete one by id
  def delete(id: Long)(using DBSession): Int =
    sql"DELETE FROM recipe_ingredients WHERE id = $id".update.apply()

  // cascade cleanup when a Recipe is deleted
  def deleteByRecipe(recipeId: Long)(using DBSession): Int =
    sql"DELETE FROM recipe_ingredients WHERE recipe_id = $recipeId".update.apply()

  // optional cleanup when a FoodItem is removed
  def deleteByFoodItem(foodItemId: Long)(using DBSession): Int =
    sql"DELETE FROM recipe_ingredients WHERE food_item_id = $foodItemId".update.apply()
