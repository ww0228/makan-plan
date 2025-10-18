package makanplan.dao

import scalikejdbc.*
import makanplan.model.User

object UserDao:
  // DAO helpers for users table

  // map a DB row to User
  private def toUser(rs: WrappedResultSet): User =
    User(
      rs.long("id"),
      rs.string("username"),
      rs.stringOpt("email").getOrElse(""),
      rs.string("password_hash")
    )
  
  // find user by id
  def findById(id: Long)(using DBSession): Option[User] =
    sql"""
      select id, username, email, password_hash
      from users
      where id = $id
    """.map(toUser).single.apply()

  // find by login name (case-insensitive)
  def findByLogin(login: String)(using DBSession): Option[User] =
    sql"""
      select id, username, email, password_hash
      from users
      where lower(username) = lower($login)
      limit 1
    """.map(toUser).single.apply()

  // find by exact username (case-insensitive)
  def findByUsername(username: String)(using DBSession): Option[User] =
    sql"""
      select id, username, email, password_hash
      from users
      where lower(username) = lower($username)
      limit 1
    """.map(toUser).single.apply()

  // check if username is taken by someone else
  def usernameExistsExcept(userId: Long, username: String)(using DBSession): Boolean =
    sql"""
      select 1
      from users
      where lower(username) = lower($username) and id <> $userId
      limit 1
    """.map(_.int(1)).single.apply().isDefined
  
  // create a new user; returns generated id
  def create(username: String, passwordHash: String, email: Option[String] = None)(using DBSession): Long =
    sql"""
      insert into users (username, email, password_hash)
      values ($username, $email, $passwordHash)
    """.updateAndReturnGeneratedKey.apply()

  // update username/email; guards against duplicate usernames
  def updateProfile(userId: Long, username: String, email: String)(using DBSession): Boolean =
    if usernameExistsExcept(userId, username) then false
    else
      sql"""
        update users
        set username = $username, email = $email
        where id = $userId
      """.update.apply() > 0

  // change password hash
  def updatePassword(userId: Long, newHash: String)(using DBSession): Boolean =
    sql"""
      update users
      set password_hash = $newHash
      where id = $userId
    """.update.apply() > 0
