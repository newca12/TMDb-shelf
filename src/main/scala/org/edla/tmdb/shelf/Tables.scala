package org.edla.tmdb.shelf

import scala.slick.driver.H2Driver.simple._
import scala.slick.lifted.{ ProvenShape, ForeignKeyQuery }

class Movies(tag: Tag)
    extends Table[(Long, String, String)](tag, "MOVIES") {

  // This is the primary key column:
  def id: Column[Long] = column[Long]("ID", O.PrimaryKey)
  def title: Column[String] = column[String]("TITLE")
  def director: Column[String] = column[String]("DIRECTOR")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Long, String, String)] =
    (id, title, director)
}

object Persist {

  val movies: TableQuery[Movies] = TableQuery[Movies]
  val db = Database.forURL(s"jdbc:h2:file:${Launcher.localStore}/tmdb-shelf", driver = "org.h2.Driver")

  try {
    db.withSession { implicit session ⇒

      // Create the schema by combining the DDLs for the Suppliers and Coffees
      // tables using the query interfaces
      (movies.ddl).create
    }
  } catch {
    case e: org.h2.jdbc.JdbcSQLException ⇒ println(e.getMessage())
  }

}
