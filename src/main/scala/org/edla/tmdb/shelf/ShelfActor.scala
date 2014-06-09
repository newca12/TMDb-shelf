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

  val tmdbClient = TmdbClient(apiKey, tmdbTimeOut)
  @volatile var nbItems = 0
  var page: Long = 1
  var maxPage: Long = 1
  val maxItems = 21
  var items: Array[javafx.scene.image.ImageView] = new Array[javafx.scene.image.ImageView](maxItems)
  var search = ""

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
        shelf.titleLabel.setText(movie.title)
        val credits = tmdbClient.getCredits(movie.id)
        credits.onSuccess {
          case c ⇒
            val director = c.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
            Launcher.scalaFxActor ! Utils.ShowItem(shelf, director)
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
    case "test" ⇒
      log.info("test asked")
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
  }

}
