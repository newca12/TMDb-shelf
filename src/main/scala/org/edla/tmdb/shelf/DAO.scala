package org.edla.tmdb.shelf

import slick.driver.H2Driver.api._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait DAOComponent {

  def insert(movie: MovieDB): Future[Int]
  def update(tmdbId: Long, movie: MovieDB): Future[Int]
  def delete(tmdbId: Long): Future[Int]
  def findById(tmdbId: Long): Future[Option[MovieDB]]
  def filter(selectedCollectionFilter: Int, selectedSearchFilter: Int, search: String): Future[Seq[MovieDB]]
  def updateSeenDate(tmdbId: Long, date: java.sql.Date): Future[Int]
  def refreshMovie(tmdbId: Long, comment: String): Future[Int]

}

object DAO extends DAOComponent {

  private val movies: TableQuery[Movies] = TableQuery[Movies]

  private val db = Database.forURL(s"jdbc:h2:file:${localStore}/tmdb-shelf", driver = "org.h2.Driver")

  try {
    Await.result(db.run((movies.schema).create), Duration.Inf)
  } catch {
    case e: org.h2.jdbc.JdbcSQLException ⇒ println(e.getMessage())
  }

  private def filterQuery(tmdbId: Long): Query[Movies, MovieDB, Seq] =
    movies.filter(_.tmdbId === tmdbId)

  override def findById(id: Long): Future[Option[MovieDB]] =
    db.run(filterQuery(id).result.headOption)

  override def insert(movie: MovieDB): Future[Int] =
    db.run(movies += movie)

  override def update(tmdbId: Long, movie: MovieDB): Future[Int] =
    db.run(filterQuery(tmdbId).update(movie))

  override def delete(tmdbId: Long): Future[Int] =
    db.run(filterQuery(tmdbId).delete)

  override def updateSeenDate(tmdbId: Long, seenDate: java.sql.Date): Future[Int] = {
    val q = for { movie ← movies if movie.tmdbId === tmdbId } yield (movie.viewingDate, movie.seen)
    db.run(q.update((Some(seenDate), true)))
  }

  override def refreshMovie(tmdbId: Long, comment: String): Future[Int] = {
    val q = for { movie ← movies if movie.tmdbId === tmdbId } yield (movie.imdbScore, movie.comment)
    db.run(filterQuery(tmdbId).result.headOption).flatMap { movie ⇒
      db.run(q.update((ImdbInfo.getScoreFromId(movie.get.imdbId), comment)))
    }
  }

  def filter(selectedCollectionFilter: Int, selectedSearchFilter: Int, search: String): Future[Seq[MovieDB]] = {

    val res_ = selectedCollectionFilter match {
      case 0 ⇒ movies.filter(_.seen === false)
        .filter(_.availability === true)
        .sortBy(m ⇒ (m.imdbScore.desc, m.releaseDate))
      case 1 ⇒ movies.sortBy(m ⇒ m.title.asc)
      case 2 ⇒ movies.filter(_.seen === true).sortBy(m ⇒ m.title.asc)
      case 3 ⇒ movies.filter(_.availability === false).sortBy(m ⇒ m.title.asc)
    }

    val res = selectedSearchFilter match {
      case 0 ⇒ res_
      case 1 ⇒ res_.filter(_.director.toLowerCase like s"%${search}%")
      case 2 ⇒ res_.filter(m ⇒ ((m.title.toLowerCase like s"%${search}%") || (m.originalTitle.toLowerCase like s"%${search}%")))
    }

    db.run(res.result)

  }
}
