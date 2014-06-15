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

object ShelfActor {
  def apply(apiKey: String, tmdbTimeOut: FiniteDuration = 5 seconds) = new ShelfActor(apiKey, tmdbTimeOut)
}

class ShelfActor(apiKey: String, tmdbTimeOut: FiniteDuration) extends Actor with akka.actor.ActorLogging {

  //TODO make localization configurable
  val tmdbClient = TmdbClient(apiKey, "fr", tmdbTimeOut)
  @volatile var nbItems = 0
  var page: Long = 1
  var maxPage: Long = 1
  val maxItems = 21
  var items: Array[javafx.scene.image.ImageView] = new Array[javafx.scene.image.ImageView](maxItems)
  var search = ""
  var selectedMovie: (Movie, Credits) = _

  def send(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, poster: javafx.scene.image.Image) = {
    var imageView_ = new ImageView()
    imageView_.setImage(poster)
    imageView_.setFitHeight(108)
    imageView_.setFitWidth(108)
    imageView_.setPreserveRatio(true)
    imageView_.setSmooth(true)
    imageView_.setOnMouseClicked(new EventHandler[MouseEvent] {
      override def handle(event: MouseEvent) {
        event.consume
        //TODO probably dangerous (ie not thread safe), use ScalaFxActor is recommended
        shelf.titleLabel.setText(movie.title)
        shelf.originalTitleLabel.setText(movie.original_title)
        shelf.releaseLabel.setText(movie.release_date)
        shelf.imdbHyperlink.setText(s"http://www.imdb.com/title/${movie.imdb_id}");
        import scala.async.Async.async
        val futureDb = async {
          import scala.slick.driver.H2Driver.simple._
          Store.db.withSession { implicit session ⇒
            //val q = for { m <- Store.movies if m.tmdbId === movie.id  } yield m.viewingDate
            val q = Store.movies.filter(_.tmdbId === movie.id)
            if (q.list.isEmpty)
              Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, None)
            else
              q.firstOption.map {
                case m: (tmdbId, releaseDate, title, originalTitle, director, addDate, viewingDate, availability, imdbID, seen) ⇒
                  Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, m._7)
                case _ ⇒ log.error("unhandled futureDb match")
              }
          }
        }
        val releases = tmdbClient.getReleases(movie.id)
        releases.onSuccess {
          case release ⇒
            Launcher.scalaFxActor ! Utils.ShowReleases(shelf, release)
        }
        releases.onFailure {
          case e: Exception ⇒
            log.error("future getReleases failed" + e.getMessage())
        }

        val credits = tmdbClient.getCredits(movie.id)
        credits.onSuccess {
          case credits ⇒
            selectedMovie = (movie, credits)
            Launcher.scalaFxActor ! Utils.RefreshDetails(shelf, movie, credits)
        }
        credits.onFailure {
          case e: Exception ⇒
            log.error("future getCredits failed" + e.getMessage())
        }
        shelf.posterImageView.setImage(poster)
        log.info(s"event for movie ${movie.id} ${movie.title}")
      }
    })

    self ! Utils.AddMovie(shelf, movie, imageView_)
  }

  def receive = {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case Utils.ChangePage(shelf, change) ⇒
      page = page + change
      Launcher.scalaFxActor ! Utils.ShowPage(shelf, page + "/" + maxPage)
      self ! Utils.Search(shelf, this.search)
    case Utils.Search(shelf, search) ⇒
      this.search = search
      //if (page == 1) {
      //items = new Array[scalafx.scene.image.ImageView](28)
      nbItems = 0
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      //}
      val results = tmdbClient.searchMovie(search, page)
      results.onSuccess {
        case results ⇒
          //if (page < results.total_pages) self ! Utils.Search(shelf, search, page + 1)
          //val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          maxPage = results.total_pages
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page + "/" + maxPage)
          for (movie ← results.results) {
            tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
            self ! Utils.GetResult(shelf, movie)
          }
      }
      results.onFailure {
        case e: Exception ⇒
          log.error("future searchMovie failed" + e.getMessage())
      }
    case "token" ⇒ sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    case Utils.GetResult(shelf, result) ⇒
      val movie = tmdbClient.getMovie(result.id)
      movie.onSuccess {
        case movie ⇒
          movie.poster_path match {
            case Some(p) ⇒
              import java.nio.file.{ Paths, Files }
              val filename = s"${Launcher.localStore}/${movie.id}.jpg"
              if (!Files.exists(Paths.get(filename))) {
                val f = tmdbClient.downloadPoster(movie, filename)
                f.onSuccess {
                  case something ⇒
                    send(shelf, movie, new Image(s"file://${filename}"))
                }
                f.onFailure {
                  case e: Exception ⇒
                    log.error("future downloadPoster failed" + e.getMessage())
                }
              } else {
                log.info("poster already there:" + movie.id)
                send(shelf, movie, new Image(s"file://${filename}"))
              }
            case None ⇒
              log.info("no poster:" + movie.id)
              send(shelf, movie, new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png"))
          }
      }
      movie.onFailure {
        case e: Exception ⇒
          log.error(s"future getMovie ${result.id} failed" + e.getMessage())
      }
    case Utils.AddMovie(shelf, movie, imageView) ⇒
      if (nbItems < maxItems) {
        Launcher.scalaFxActor ! Utils.AddPoster(shelf, movie, imageView, Utils.Position(nbItems % 7, nbItems / 7))
        items(nbItems) = imageView
      }
      nbItems = nbItems + 1
    case Utils.SaveMovie(shelf) ⇒
      import scala.slick.driver.H2Driver.simple._
      val movie = selectedMovie._1
      val credits = selectedMovie._2
      val director = credits.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
      Store.db.withSession { implicit session ⇒
        val tmp = (movie.id, java.sql.Date.valueOf(movie.release_date), movie.title, movie.original_title, director,
          new java.sql.Date(new java.util.Date().getTime()), None, true, movie.imdb_id, false)
        Store.movies += tmp
      }
      log.info(s"${movie.id} ${movie.title} registered")
      Launcher.scalaFxActor ! Utils.RefreshDetails(shelf, movie, credits)
    case Utils.ShowCollection(shelf) ⇒
      import scala.slick.driver.H2Driver.simple._
      nbItems = 0
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      Store.db.withSession { implicit session ⇒

        Store.movies foreach {
          //TODO match seen true/false
          case (tmdbId, releaseDate, title, originalTitle, director, addDate, viewingDate, availability, imdbID, seen) ⇒
            val movie = tmdbClient.getMovie(tmdbId)
            movie.onSuccess {
              case movie ⇒
                val filename = s"${Launcher.localStore}/${movie.id}.jpg"
                send(shelf, movie, new Image(s"file://${filename}"))
            }
            movie.onFailure {
              case e: Exception ⇒
                log.error(s"future viewCollection getMovie failed" + e.getMessage())
            }
        }
      }
    case Utils.SaveSeenDate(shelf, seenDate) ⇒
      import scala.slick.driver.H2Driver.simple._
      Store.db.withSession { implicit session ⇒
        val q = for { movie ← Store.movies if movie.tmdbId === selectedMovie._1.id } yield (movie.viewingDate, movie.seen)
        q.update((Some(seenDate), true))
      }
  }

}
