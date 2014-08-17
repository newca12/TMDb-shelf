package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps
import akka.event.Logging
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import org.edla.tmdb.api._
import org.edla.tmdb.api.Protocol._
import scala.util.Success
import scala.slick.driver.H2Driver.simple._
import java.nio.file.{ Paths, Files }

object ShelfActor {
  def apply(apiKey: String, tmdbTimeOut: FiniteDuration = 5 seconds) = new ShelfActor(apiKey, tmdbTimeOut)
}

class ShelfActor(apiKey: String, tmdbTimeOut: FiniteDuration) extends Actor with akka.actor.ActorLogging {

  val tmdbClient = TmdbClient(apiKey, java.util.Locale.getDefault().getLanguage, tmdbTimeOut)
  @volatile var nbItems = 0
  var page: Long = 1
  var maxPage: Long = 1
  val maxItems = 40
  var items: Array[javafx.scene.image.ImageView] = new Array[javafx.scene.image.ImageView](maxItems)
  var search = ""
  var selectedMovie: Long = _
  var selectedFilter: Number = 0

  def refreshInfo(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Long) {
    val releases = tmdbClient.getReleases(tmdbId)
    releases.onSuccess {
      case release ⇒
        Launcher.scalaFxActor ! Utils.ShowReleases(shelf, release)
    }
    releases.onFailure {
      case e: Exception ⇒
        log.error("future getReleases failed" + e.getMessage())
    }
    val credits = tmdbClient.getCredits(tmdbId)
    credits.onSuccess {
      case credits ⇒
        selectedMovie = tmdbId
        Launcher.scalaFxActor ! Utils.RefreshCredits(shelf, tmdbId, credits)
    }
    credits.onFailure {
      case e: Exception ⇒
        log.error("future getCredits failed" + e.getMessage())
    }
  }

  def addToShelf_(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, poster: javafx.scene.image.Image) = {
    addToShelf(shelf, movie.id, movie.release_date.toString, movie.title, movie.original_title, movie.imdb_id, poster)
  }

  def addToShelf(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Long, releaseDate: String, title: String, originalTitle: String, imdbID: String, poster: javafx.scene.image.Image) = {
    val ds = new javafx.scene.effect.DropShadow()
    ds.setOffsetY(-5.0);
    ds.setOffsetX(5.0);
    ds.setColor(javafx.scene.paint.Color.BLACK)
    var imageView_ = new ImageView()
    imageView_.setImage(poster)
    imageView_.setFitHeight(108)
    imageView_.setFitWidth(108)
    imageView_.setPreserveRatio(true)
    imageView_.setSmooth(true)
    imageView_.setEffect(ds)
    imageView_.setOnMouseClicked(new EventHandler[MouseEvent] {
      override def handle(event: MouseEvent) {
        event.consume
        selectedMovie = tmdbId
        shelf.posterImageView.setImage(poster)
        Launcher.scalaFxActor ! Utils.RefreshMovie(shelf, title, originalTitle, releaseDate, imdbID)
        import scala.async.Async.async
        val score = async {
          val score = ImdbScore.getScoreFromId(imdbID)
          Launcher.scalaFxActor ! Utils.RefreshScore(shelf, score)
        }
        val futureDb = async {
          Store.db.withSession { implicit session ⇒
            val q = Store.movies.filter(_.tmdbId === tmdbId)
            if (q.list.isEmpty)
              Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, None)
            else
              q.firstOption.map {
                case m: (tmdbId, releaseDate, title, originalTitle, director, addDate, viewingDate, availability, imdbID, imdbScore, seen) ⇒
                  Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, m._7)
                case _ ⇒ log.error("unhandled futureDb match")
              }
          }
        }
        refreshInfo(shelf, tmdbId)
        log.debug(s"event for movie ${tmdbId} ${title}")
      }
    })
    self ! Utils.AddPoster(shelf, imageView_)
  }

  def receive = {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case Utils.ChangePage(shelf, change) ⇒
      page = page + change
      if (search.length() > 0) self ! Utils.Search(shelf, this.search, false)
      else self ! Utils.ShowCollection(shelf, false)
    case Utils.Search(shelf, search, user) ⇒
      if (user) page = 1
      this.search = search
      nbItems = 0
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      val results = tmdbClient.searchMovie(search, page * 2 - 1)
      results.onSuccess {
        case results ⇒
          maxPage = results.total_pages
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page + "/" + Math.ceil(maxPage / 2.0).toLong)
          for (movie ← results.results) {
            tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
            self ! Utils.GetResult(shelf, movie)
          }
          if (page < maxPage) {
            val results = tmdbClient.searchMovie(search, page * 2)
            results.onSuccess {
              case results ⇒
                for (movie ← results.results) {
                  tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
                  self ! Utils.GetResult(shelf, movie)
                }
            }
            results.onFailure {
              case e: Exception ⇒
                log.error("future searchMovie second page failed" + e.getMessage())
            }
          }
      }
      results.onFailure {
        case e: Exception ⇒
          log.error("future searchMovie first page failed" + e.getMessage())
      }
    case "token" ⇒ sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    case Utils.GetResult(shelf, result) ⇒
      val movie = tmdbClient.getMovie(result.id)
      movie.onSuccess {
        case movie ⇒
          import java.nio.file.{ Paths, Files }
          val filename = s"${Launcher.tmpDir}/${movie.id}.jpg"
          if (!Files.exists(Paths.get(filename))) {
            val f = tmdbClient.downloadPoster(movie, filename)
            f.onSuccess {
              case true ⇒
                addToShelf_(shelf, movie, new Image(s"file://${filename}"))
              case false ⇒
                addToShelf_(shelf, movie, new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png"))
            }
            f.onFailure {
              case e: Exception ⇒
                log.error("future downloadPoster failed" + e.getMessage())
            }
          } else {
            log.info("poster already there:" + movie.id)
            addToShelf_(shelf, movie, new Image(s"file://${filename}"))
          }
      }
      movie.onFailure {
        case e: Exception ⇒
          log.error(s"future getMovie ${result.id} failed" + e.getMessage())
      }
    case Utils.AddPoster(shelf, imageView) ⇒
      if (nbItems < maxItems) {
        Launcher.scalaFxActor ! Utils.AddPosterXy(shelf, imageView, Utils.Position(nbItems % 8, nbItems / 8))
        items(nbItems) = imageView
      }
      nbItems = nbItems + 1
    case Utils.SaveMovie(shelf) ⇒
      val filename = s"${Launcher.tmpDir}/${selectedMovie}.jpg"
      if (Files.exists(Paths.get(filename)))
        Files.copy(Paths.get(filename), Paths.get(s"${Store.localStore}/${selectedMovie}.jpg"))
      val movie = Await.result(tmdbClient.getMovie(selectedMovie), 5 seconds)
      val credits = Await.result(tmdbClient.getCredits(selectedMovie), 5 seconds)
      val director = credits.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
      try {
        Store.db.withSession { implicit session ⇒
          val tmp = (movie.id, java.sql.Date.valueOf(movie.release_date), movie.title, movie.original_title, director,
            new java.sql.Date(new java.util.Date().getTime()), None, true, movie.imdb_id, ImdbScore.getScoreFromId(movie.imdb_id), false)
          Store.movies += tmp
        }
        log.info(s"${movie.title} registered")
        refreshInfo(shelf, movie.id)
        Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Registered")
      } catch {
        case e: Exception ⇒
          log.error(e.getMessage())
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }
    //Launcher.scalaFxActor ! Utils.RefreshCredits(shelf, movie.id, credits)
    case Utils.RemoveMovie(shelf) ⇒
      val movie = Await.result(tmdbClient.getMovie(selectedMovie), 5 seconds)
      Launcher.scalaFxActor ! Utils.ConfirmDeletion(shelf, movie)
    case Utils.DeletionConfirmed(shelf, movie) ⇒
      try {
        Store.db.withSession { implicit session ⇒
          Store.movies.filter(_.tmdbId === movie.id).delete
        }
        Files.delete(Paths.get(s"${Store.localStore}/${movie.id}.jpg"))
        log.warning(s"Movie ${movie.id} ${movie.title} removed")
        Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "REMOVED")
        refreshInfo(shelf, movie.id)
      } catch {
        case e: Exception ⇒
          log.error(e.getMessage())
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }
    case Utils.ShowCollection(shelf, user) ⇒
      nbItems = 0
      search = ""
      if (user) page = 1
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      Store.db.withSession { implicit session ⇒
        val res = selectedFilter.intValue() match {
          case 0 ⇒ Store.movies.sortBy(m ⇒ m.title.asc)
          case 1 ⇒ Store.movies.filter(_.seen === true).sortBy(m ⇒ m.title.asc)
          case 2 ⇒ Store.movies.filter(_.seen === false)
            .filter(_.availability === true)
            .sortBy(m ⇒ (m.imdbScore.desc, m.releaseDate))
          case 3 ⇒ Store.movies.filter(_.availability === false).sortBy(m ⇒ m.title.asc)
        }
        //TODO not efficient
        maxPage = (res.list.size / maxItems) + 1
        Launcher.scalaFxActor ! Utils.ShowPage(shelf, page + "/" + maxPage)
        res.drop((page - 1) * maxItems).take(maxItems) foreach {
          //TODO match seen true/false
          case (tmdbId, releaseDate, title, originalTitle, director, addDate, viewingDate, availability, imdbID, imdbScore, seen) ⇒
            val filename = s"${Store.localStore}/${tmdbId}.jpg"
            val image =
              if (Files.exists(Paths.get(filename))) new Image(s"file://${filename}")
              else new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png")
            addToShelf(shelf, tmdbId, releaseDate.toString, title, originalTitle, imdbID, image)
        }
      }
    case Utils.SaveSeenDate(shelf, seenDate) ⇒
      try {
        Store.db.withSession { implicit session ⇒
          val q = for { movie ← Store.movies if movie.tmdbId === selectedMovie } yield (movie.viewingDate, movie.seen)
          val res = q.update((Some(seenDate), true))
        }
        Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Date updated")
      } catch {
        case e: Exception ⇒
          log.error(e.getMessage())
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }
    case Utils.SetFilter(shelf, filter) ⇒
      selectedFilter = filter
  }

}
