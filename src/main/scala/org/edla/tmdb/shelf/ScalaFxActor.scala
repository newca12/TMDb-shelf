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

  var nbItems = 0
  var items: Array[scalafx.scene.image.ImageView] = new Array[scalafx.scene.image.ImageView](28)

  def receive = {
    case "reset" =>
      nbItems = 0
      items = new Array[scalafx.scene.image.ImageView](28)
    case Utils.Add(shelf, movieId) =>
      items(nbItems) = new ImageView {
        //CAUTION id is interpreted in String interpolation !
        image = new Image(s"file:///tmp/${movieId}.jpg")
        fitHeight_=(108)
        fitWidth_=(108)
        preserveRatio = true
        smooth = true
        onMouseClicked = new EventHandler[MouseEvent] {
          override def handle(event: MouseEvent) {
            event.consume
            println(s"event for movie ${movieId}")
          }
        }
      }
      shelf.add(items(nbItems), nbItems % 7, nbItems / 7)
      nbItems = nbItems + 1
    case _ => println("nops")
  }

}