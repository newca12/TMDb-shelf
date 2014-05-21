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
import scalafx.scene.image.Image
import scalafx.scene.image.ImageView
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent

object ShelfActor {
  def apply(apiKey: String, tmdbTimeOut: FiniteDuration = 5 seconds) = new ShelfActor(apiKey, tmdbTimeOut)
}

class ShelfActor(apiKey: String, tmdbTimeOut: FiniteDuration) extends Actor with akka.actor.ActorLogging {

  val tmdbClient = TmdbClient(apiKey, tmdbTimeOut)
  @volatile var nbItems = 0
  var items: Array[scalafx.scene.image.ImageView] = new Array[scalafx.scene.image.ImageView](28)

  def send(shelf: scalafx.scene.layout.GridPane, movie: org.edla.tmdb.api.Movie, poster: scalafx.scene.image.Image) = {
    val imageView_ = new ImageView {
      //CAUTION id is interpreted in String interpolation !
      image = poster
      fitHeight_=(108)
      fitWidth_=(108)
      preserveRatio = true
      smooth = true
      onMouseClicked = new EventHandler[MouseEvent] {
        override def handle(event: MouseEvent) {
          event.consume
          log.info(s"event for movie ${movie.id} ${movie.title}")
        }
      }
    }
    self ! Utils.AddMovie(shelf, movie, imageView_)
  }

  def receive = {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case Utils.Search(shelf, search) ⇒
      nbItems = 0
      //items = new Array[scalafx.scene.image.ImageView](28)
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      val results = tmdbClient.searchMovie(search)
      results.onSuccess {
        case results ⇒
          //val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          for (movie ← results.results) {
            tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
            self ! Utils.GetResult(shelf, movie)
          }
      }
      results.onFailure {
        case e: Exception ⇒
          log.error("future searchmovie failed" + e.getMessage())
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
              send(shelf, movie, new Image(this, "view/images/200px-No_image_available.svg.png"))
          }
      }
      movie.onFailure {
        case e: Exception ⇒
          log.error("future getMovie failed" + e.getMessage())
      }
    case Utils.AddMovie(shelf, movie, imageView) ⇒
      Launcher.scalaFxActor ! Utils.AddPoster(shelf, movie, imageView, Utils.Position(nbItems % 7, nbItems / 7))
      items(nbItems) = imageView
      nbItems = nbItems + 1
  }

}
