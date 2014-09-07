package org.edla.tmdb.shelf

import scala.slick.driver.H2Driver.simple._
import scala.slick.lifted.{ ProvenShape, ForeignKeyQuery }
import java.sql.Date

class Movies(tag: Tag)
    extends Table[(Long, Date, String, String, String, Date, Option[Date], Boolean, String, Option[BigDecimal], Boolean, String)](tag, "MOVIES") {

  // This is the primary key column:
  def tmdbId = column[Long]("TMDB_ID", O.PrimaryKey)
  def releaseDate = column[Date]("RELEASE_DATE")
  def title = column[String]("TITLE")
  def originalTitle = column[String]("ORIGINAL_TITLE")
  def director = column[String]("DIRECTOR")
  def addDate = column[Date]("ADD_DATE")
  def viewingDate = column[Option[Date]]("VIEWING_DATE")
  def availability = column[Boolean]("AVAILABILITY")
  def imdbID = column[String]("IMDB_ID")
  def imdbScore = column[Option[BigDecimal]]("IMDB_SCORE", O.DBType("DECIMAL(2,1)"))
  def seen = column[Boolean]("SEEN")
  def comment = column[String]("COMMENT")

  // Every table needs a * projection with the same type as the table's type parameter
  def * : ProvenShape[(Long, Date, String, String, String, Date, Option[Date], Boolean, String, Option[BigDecimal], Boolean, String)] =
    (tmdbId, releaseDate, title, originalTitle, director, addDate, viewingDate, availability, imdbID, imdbScore, seen, comment)
}

object Store {

  val home = System.getProperty("user.home")
  val localStore = s"${home}/.tmdb-shelf"
  val movies: TableQuery[Movies] = TableQuery[Movies]
  val db = Database.forURL(s"jdbc:h2:file:${localStore}/tmdb-shelf", driver = "org.h2.Driver")

  try {
    db.withSession { implicit session ⇒
      (movies.ddl).create
    }
  } catch {
    case e: org.h2.jdbc.JdbcSQLException ⇒ println(e.getMessage())
  }

}
