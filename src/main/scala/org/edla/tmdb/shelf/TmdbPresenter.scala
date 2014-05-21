package org.edla.tmdb.shelf

import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.control.TextField
import scalafx.scene.layout.GridPane
import scalafx.scene.image.Image
import scalafx.scene.image.ImageView
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor.ActorSystem
import akka.actor.ActorContext
import akka.actor.Props
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import org.edla.tmdb.client.TmdbClient

@sfxml
class TmdbPresenter(
    private val search: TextField,
    private val shelf: GridPane,
    private val check_button: Button) {

  import scalafx.Includes._
  import scala.concurrent._
  import ExecutionContext.Implicits.global
  check_button.onAction = { (_: ActionEvent) ⇒
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.Search(shelf, search.text.value)
  }

  def check(event: ActionEvent) {

  }

  def handleClear(event: ActionEvent) {
    search.text = ""
  }

}