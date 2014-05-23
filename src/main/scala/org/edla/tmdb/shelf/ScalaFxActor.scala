package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps
import scala.concurrent._
import ExecutionContext.Implicits.global

class ScalaFxActor extends Actor {

  import scalafx.scene.image.Image
  import scalafx.scene.image.ImageView
  import scalafx.event.ActionEvent
  import javafx.event.EventHandler
  import javafx.scene.input.MouseEvent

  def receive = {
    case Utils.Reset(shelf, items) ⇒
      for (item ← items)
        shelf.shelfGridPane.getChildren().remove(item)

    case Utils.AddPoster(shelf, movie, poster, pos) ⇒
      shelf.shelfGridPane.add(poster, pos.x, pos.y)

    case Utils.ShowItem(shelf, item) ⇒
      shelf.directorLabel.setText(item)

  }

}