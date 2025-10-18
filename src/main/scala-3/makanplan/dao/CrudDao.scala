package makanplan.dao
import scalikejdbc.*

trait CrudDao[T, K]:
  def findById(id: K)(using DBSession): Option[T]
  def create(t: T)(using DBSession): Long
  def update(t: T)(using DBSession): Int
  def delete(id: K)(using DBSession): Int
