package org.edla.tmdb.shelf

import slick.jdbc.H2Profile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

trait DAOComponent {

  def insert(movie: MovieDB): Future[Int]
  def update(tmdbId: Int, movie: MovieDB): Future[Int]
  def delete(tmdbId: Int): Future[Int]
  def findById(tmdbId: Int): Future[Option[MovieDB]]
  def filter(selectedCollectionFilter: Int, selectedSearchFilter: Int, search: String): Future[Seq[MovieDB]]
  def updateSeenDate(tmdbId: Int, date: java.sql.Date): Future[Int]
  def refreshMovie(tmdbId: Int, comment: String, viewable: Boolean): Future[Int]
  def saveRunTime(tmdbId: Int, runTime: Int): Future[Int]
}

object DAO extends DAOComponent {

  private val movies: TableQuery[Movies] = TableQuery[Movies]

  private val db = Database.forURL(s"jdbc:h2:file:$localStore/tmdb-shelf", driver = "org.h2.Driver")

  try {
    Await.result(db.run(movies.schema.create), Duration.Inf)
  } catch {
    case e: org.h2.jdbc.JdbcSQLException ⇒ println(e.getMessage)
  }

  private def filterQuery(tmdbId: Int): Query[Movies, MovieDB, Seq] =
    movies.filter(_.tmdbId === tmdbId)

  override def findById(id: Int): Future[Option[MovieDB]] =
    db.run(filterQuery(id).result.headOption)

  override def insert(movie: MovieDB): Future[Int] =
    db.run(movies += movie)

  override def update(tmdbId: Int, movie: MovieDB): Future[Int] =
    db.run(filterQuery(tmdbId).update(movie))

  override def delete(tmdbId: Int): Future[Int] =
    db.run(filterQuery(tmdbId).delete)

  override def updateSeenDate(tmdbId: Int, seenDate: java.sql.Date): Future[Int] = {
    val q = for { movie ← movies if movie.tmdbId === tmdbId } yield (movie.viewingDate, movie.seen)
    db.run(q.update((Some(seenDate), true)))
  }

  override def refreshMovie(tmdbId: Int, comment: String, viewable: Boolean): Future[Int] = {
    val q = for { movie ← movies if movie.tmdbId === tmdbId } yield (movie.imdbScore, movie.comment, movie.viewable)
    db.run(filterQuery(tmdbId).result.headOption).flatMap { movie ⇒
      db.run(q.update((ImdbInfo.getScoreFromId(movie.get.imdbId), comment, viewable)))
    }
  }

  override def saveRunTime(tmdbId: Int, runTime: Int): Future[Int] = {
    val q = for { movie ← movies if movie.tmdbId === tmdbId } yield (movie.runTime)
    db.run(filterQuery(tmdbId).result.headOption).flatMap { movie ⇒
      db.run(q.update((Some(runTime))))
    }
  }

  def filter(selectedCollectionFilter: Int, selectedSearchFilter: Int, search: String): Future[Seq[MovieDB]] = {

    val res_ = selectedCollectionFilter match {
      case 0 ⇒
        movies
          .filter(_.seen === false)
          .filter(_.availability === true)
          .filter(_.viewable === true)
          .sortBy(m ⇒ (m.imdbScore.desc, m.releaseDate))
      case 1 ⇒ movies.sortBy(m ⇒ m.title.asc)
      case 2 ⇒ movies.filter(_.seen === true).sortBy(m ⇒ m.title.asc)
      case 3 ⇒ movies.filter(_.availability === false).sortBy(m ⇒ m.title.asc)
      case 4 ⇒ movies.filter(_.viewable === false).sortBy(m ⇒ (m.imdbScore.desc, m.releaseDate))
    }

    val res = selectedSearchFilter match {
      case 0 ⇒ res_
      case 1 ⇒ res_.filter(_.director.toLowerCase like s"%$search%")
      case 2 ⇒
        res_.filter(
          m ⇒
            (m.title.toLowerCase like s"%$search%") ||
              (m.originalTitle.toLowerCase like s"%$search%"))
    }

    db.run(res.result)
  }
}
