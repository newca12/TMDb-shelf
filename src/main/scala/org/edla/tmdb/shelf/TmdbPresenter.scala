package org.edla.tmdb.shelf

//import scalafxml.core.macros.sfxml
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.TextField
import javafx.scene.control.Label
import javafx.scene.layout.GridPane
import javafx.scene.layout.AnchorPane
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import akka.actor.ActorSystem
import akka.actor.ActorContext
import akka.actor.Props
import scala.concurrent.Future
import akka.pattern.ask
import akka.util.Timeout
import org.edla.tmdb.client.TmdbClient

import javafx.scene.{ control ⇒ jfxsc }
import javafx.scene.{ layout ⇒ jfxsl }
import javafx.scene.{ image ⇒ jfxsi }
import javafx.{ event ⇒ jfxe }
import javafx.{ fxml ⇒ jfxf }

class TmdbPresenter {
  @jfxf.FXML
  var searchTextField: jfxsc.TextField = _
  @jfxf.FXML
  var searchButton: jfxsc.Button = _
  @jfxf.FXML
  var shelfGridPane: jfxsl.GridPane = _
  @jfxf.FXML
  var detailsAnchorPane: jfxsl.AnchorPane = _
  @jfxf.FXML
  var titleLabel: jfxsc.Label = _
  @jfxf.FXML
  var posterImageView: jfxsi.ImageView = _
  @jfxf.FXML
  var directorLabel: jfxsc.Label = _

  /* var searchTextFiled: TextField = _
  var shelfGridPane: GridPane = _
  var searchButton: Button = _
  var detailsAnchorPane: AnchorPane = _
  var titleLabel: Label = _
  var posterImageView: ImageView = _
  var directorLabel: Label = _*/

  import scala.concurrent._
  import ExecutionContext.Implicits.global

  @jfxf.FXML
  def search(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    titleLabel.setText(searchTextField.getText())
    shelfActor ! Utils.Search(this, searchTextField.getText(), 1)
  }

  @jfxf.FXML
  def handleClear(event: jfxe.ActionEvent) {
    searchTextField.setText("")
  }
}
