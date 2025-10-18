package makanplan.dao

import scalikejdbc.*
import makanplan.model.FoodItem

object FoodItemDao extends CrudDao[FoodItem, Long]:

  // map a DB row to FoodItem
  private def map(rs: WrappedResultSet): FoodItem =
    FoodItem(
      id       = rs.long("id"),
      userId   = rs.long("user_id"),
      name     = rs.string("name"),
      category = rs.string("category"),
      quantity = rs.double("quantity"),
      unit     = rs.string("unit"),
      expiry   = rs.localDate("expiry"),
      cost     = rs.double("cost")
    )

  // find one by id
  def findById(id: Long)(using DBSession): Option[FoodItem] =
    sql"select * from food_items where id = $id".map(map).single.apply()

  // all items for a user, sorted by expiry then name
  def findAllByUser(userId: Long)(using DBSession): List[FoodItem] =
    sql"select * from food_items where user_id = $userId order by expiry asc, name asc"
      .map(map).list.apply()

  // items expiring within N days for a user
  def findExpiringSoon(userId: Long, days: Int)(using DBSession): List[FoodItem] =
    sql"""select * from food_items
          where user_id = $userId
            and expiry <= ${java.time.LocalDate.now.plusDays(days.toLong)}
          order by expiry asc"""
      .map(map).list.apply()

  // insert and return generated id
  def create(t: FoodItem)(using DBSession): Long =
    sql"""
      insert into food_items (user_id, name, category, quantity, unit, expiry, cost)
      values (${t.userId}, ${t.name}, ${t.category}, ${t.quantity}, ${t.unit}, ${t.expiry}, ${t.cost})
    """.updateAndReturnGeneratedKey.apply()

  // update by id scoped to userId
  def update(t: FoodItem)(using DBSession): Int =
    sql"""
      update food_items
      set name=${t.name}, category=${t.category}, quantity=${t.quantity},
          unit=${t.unit}, expiry=${t.expiry}, cost=${t.cost}
      where id=${t.id} and user_id=${t.userId}
    """.update.apply()

  // delete by id
  def delete(id: Long)(using DBSession): Int =
    sql"delete from food_items where id = $id".update.apply()
