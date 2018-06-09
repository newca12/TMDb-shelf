package org.edla.tmdb.shelf

import slick.jdbc.H2Profile.api._
import java.sql.Date

case class MovieDB(
    tmdbId: Int,
    releaseDate: Date,
    title: String,
    originalTitle: String,
    director: String,
    addDate: Date,
    viewingDate: Option[Date],
    availability: Boolean,
    imdbId: String,
    imdbScore: Option[BigDecimal],
    seen: Boolean,
    comment: String,
    viewable: Boolean,
    runTime: Option[Int]
)

class Movies(tag: Tag) extends Table[MovieDB](tag, "MOVIES") {

  // This is the primary key column:
  // scalastyle:off public.methods.have.type
  def tmdbId        = column[Int]("TMDB_ID", O.PrimaryKey)
  def releaseDate   = column[Date]("RELEASE_DATE")
  def title         = column[String]("TITLE")
  def originalTitle = column[String]("ORIGINAL_TITLE")
  def director      = column[String]("DIRECTOR")
  def addDate       = column[Date]("ADD_DATE")
  def viewingDate   = column[Option[Date]]("VIEWING_DATE")
  def availability  = column[Boolean]("AVAILABILITY")
  def imdbId        = column[String]("IMDB_ID")
  def imdbScore =
    column[Option[BigDecimal]]("IMDB_SCORE", O.SqlType("DECIMAL(2,1)"))
  def seen     = column[Boolean]("SEEN")
  def comment  = column[String]("COMMENT")
  def viewable = column[Boolean]("VIEWABLE")
  def runTime  = column[Option[Int]]("RUNTIME")

  // Every table needs a * projection with the same type as the table's type parameter
  // scalastyle:off method.name
  def * =
    (
      tmdbId,
      releaseDate,
      title,
      originalTitle,
      director,
      addDate,
      viewingDate,
      availability,
      imdbId,
      imdbScore,
      seen,
      comment,
      viewable,
      runTime
    ) <>
      (MovieDB.tupled, MovieDB.unapply _)
}
