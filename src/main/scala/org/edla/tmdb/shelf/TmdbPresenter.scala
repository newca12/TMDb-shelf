package org.edla.tmdb.shelf

//import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.control.TextField
import scalafx.scene.control.Label
import scalafx.scene.layout.GridPane
import scalafx.scene.layout.AnchorPane
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

import javafx.scene.{ control ⇒ jfxsc }
import javafx.scene.{ layout ⇒ jfxsl }
import javafx.scene.{ image ⇒ jfxsi }
import javafx.{ event ⇒ jfxe }
import javafx.{ fxml ⇒ jfxf }

class TmdbPresenter extends jfxf.Initializable {
  @jfxf.FXML
  private var search_texfield: jfxsc.TextField = _
  @jfxf.FXML
  private var search_button: jfxsc.Button = _
  @jfxf.FXML
  private var shelf_gridpane: jfxsl.GridPane = _
  @jfxf.FXML
  private var details_anchorpane: jfxsl.AnchorPane = _
  @jfxf.FXML
  private var title_label: jfxsc.Label = _
  @jfxf.FXML
  private var poster_imageview: jfxsi.ImageView = _
  @jfxf.FXML
  private var director_label: jfxsc.Label = _

  var searchTextFiled: TextField = _
  var shelfGridPane: GridPane = _
  var searchButton: Button = _
  var detailsAnchorPane: AnchorPane = _
  var titleLabel: Label = _
  var posterImageView: ImageView = _
  var directorLabel: Label = _

  import scalafx.Includes._
  import scala.concurrent._
  import ExecutionContext.Implicits.global

  @jfxf.FXML
  def search(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    title_label.text = search_texfield.text.value
    shelfActor ! Utils.Search(this, search_texfield.text.value)
  }

  @jfxf.FXML
  def handleClear(event: jfxe.ActionEvent) {
    search_texfield.text = ""
  }

  import java.net.URL
  import java.util
  override def initialize(url: URL, rb: util.ResourceBundle) {
    searchTextFiled = new TextField(search_texfield)
    searchButton = new Button(search_button)
    shelfGridPane = new GridPane(shelf_gridpane)
    detailsAnchorPane = new AnchorPane(details_anchorpane)
    titleLabel = new Label(title_label)
    posterImageView = new ImageView(poster_imageview)
    directorLabel = new Label(director_label)
  }

}