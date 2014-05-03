package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps

class ScalaFxActor extends Actor {

  import scalafx.scene.image.Image
  import scalafx.scene.image.ImageView
  import scalafx.event.ActionEvent
  import javafx.event.EventHandler
  import javafx.scene.input.MouseEvent

  val tmdbClient = Utils.getTmdbClient
  var nbItems = 0
  var items: Array[scalafx.scene.image.ImageView] = new Array[scalafx.scene.image.ImageView](28)

  def receive = {
    case Utils.Reset(shelf) ⇒
      nbItems = 0
      //items = new Array[scalafx.scene.image.ImageView](28)
      for (item ← items)
        shelf.getChildren().remove(item)

    case Utils.Add(shelf, movie) ⇒
      val m = Await.result(tmdbClient.getMovie(movie.id), 5 seconds)
      m.poster_path match {
        case Some(p) ⇒
          import java.nio.file.{ Paths, Files }
          val filename = s"/tmp/${movie.id}.jpg"
          if (!Files.exists(Paths.get(filename)))
            Await.result(tmdbClient.downloadPoster(m, filename), 5 seconds)
          items(nbItems) = new ImageView {
            //CAUTION id is interpreted in String interpolation !
            image = new Image(s"file://${filename}")
            fitHeight_=(108)
            fitWidth_=(108)
            preserveRatio = true
            smooth = true
            onMouseClicked = new EventHandler[MouseEvent] {
              override def handle(event: MouseEvent) {
                event.consume
                println(s"event for movie ${movie}")
              }
            }
          }
          shelf.add(items(nbItems), nbItems % 7, nbItems / 7)
          nbItems = nbItems + 1
        case None ⇒
          tmdbClient.log.info("no poster")
          items(nbItems) = new ImageView {
            //CAUTION id is interpreted in String interpolation !
            image = new Image(this, "view/images/200px-No_image_available.svg.png")
            fitHeight_=(108)
            fitWidth_=(108)
            preserveRatio = true
            smooth = true
            onMouseClicked = new EventHandler[MouseEvent] {
              override def handle(event: MouseEvent) {
                event.consume
                println(s"event for movie ${movie}")
              }
            }
          }
          shelf.add(items(nbItems), nbItems % 7, nbItems / 7)
          nbItems = nbItems + 1
      }

    case _ ⇒ println("nops")
  }

}