package org.edla.tmdb.shelf

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

import javafx.collections.FXCollections
import javafx.fxml.Initializable

class TmdbPresenter extends Initializable {
  @jfxf.FXML
  var previousButton: jfxsc.Button = _
  @jfxf.FXML
  var pageLabel: jfxsc.Label = _
  @jfxf.FXML
  var nextButton: jfxsc.Button = _
  @jfxf.FXML
  var showCollectionButton: jfxsc.Button = _
  @jfxf.FXML
  var filterCollectionChoiceBox: jfxsc.ChoiceBox[String] = _
  @jfxf.FXML
  var searchButton: jfxsc.Button = _
  @jfxf.FXML
  var searchTextField: jfxsc.TextField = _
  @jfxf.FXML
  var filterSearchChoiceBox: jfxsc.ChoiceBox[String] = _
  @jfxf.FXML
  var shelfGridPane: jfxsl.GridPane = _
  @jfxf.FXML
  var detailsAnchorPane: jfxsl.AnchorPane = _
  @jfxf.FXML
  var posterImageView: jfxsi.ImageView = _
  @jfxf.FXML
  var addMovieButton: jfxsc.Button = _
  @jfxf.FXML
  var refreshMovieButton: jfxsc.Button = _
  @jfxf.FXML
  var deleteMovieButton: jfxsc.Button = _
  @jfxf.FXML
  var titleLabel: jfxsc.Label = _
  @jfxf.FXML
  var originalTitleLabel: jfxsc.Label = _
  @jfxf.FXML
  var directorLabel: jfxsc.Label = _
  @jfxf.FXML
  var releaseLabel: jfxsc.Label = _
  @jfxf.FXML
  var runtimeLabel: jfxsc.Label = _
  @jfxf.FXML
  var localizedReleaseLabel: jfxsc.Label = _
  @jfxf.FXML
  var seenDatePicker: jfxsc.DatePicker = new jfxsc.DatePicker()
  @jfxf.FXML
  var saveSeenDateButton: jfxsc.Button = _
  @jfxf.FXML
  var imdbHyperlink: jfxsc.Hyperlink = new jfxsc.Hyperlink("http://www.imdb.com")
  @jfxf.FXML
  var scoreLabel: jfxsc.Label = _
  @jfxf.FXML
  var scoreImageView: jfxsi.ImageView = _
  @jfxf.FXML
  var tmdbHyperlink: jfxsc.Hyperlink = new jfxsc.Hyperlink("http://www.themoviedb.org/")
  @jfxf.FXML
  var commentTextArea: jfxsc.TextArea = _

  import scala.concurrent._
  import ExecutionContext.Implicits.global

  import java.net.URL
  import java.util.ResourceBundle
  import javafx.beans.value.ChangeListener
  import javafx.beans.value.ObservableValue
  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle) {
    //filterCollectionChoiceBox = new jfxsc.ChoiceBox(FXCollections.observableArrayList("filter 1", "filter 2", "filter 3"))
    filterCollectionChoiceBox.getItems().addAll("Not seen", "All", "Seen", "Not available")
    filterCollectionChoiceBox.getSelectionModel().selectFirst()
    filterCollectionChoiceBox.getSelectionModel().selectedIndexProperty().addListener(
      new ChangeListener[Number]() {
        def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number) {
          val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          shelfActor ! Utils.SetCollectionFilter(TmdbPresenter.this, newValue)
        }
      }
    )

    filterSearchChoiceBox.getItems().addAll("All", "Director", "Movie name")
    filterSearchChoiceBox.getSelectionModel().selectFirst()
    filterSearchChoiceBox.getSelectionModel().selectedIndexProperty().addListener(
      new ChangeListener[Number]() {
        def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number) {
          val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          shelfActor ! Utils.SetSearchFilter(TmdbPresenter.this, newValue)
        }
      }
    )
  }

  @jfxf.FXML
  def previousPage(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ChangePage(this, -1)
  }

  def nextPage(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ChangePage(this, 1)
  }

  def showCollection(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ShowCollection(this, searchTextField.getText(), true)
  }

  def addMovie(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.SaveMovie(this)
  }

  def refreshMovie(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.RefreshMovie(this)
  }

  def deleteMovie(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.RemoveMovie(this)
  }

  def search(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.Search(this, searchTextField.getText().replace('.', ' '), true)
  }

  def handleClear(event: jfxe.ActionEvent) {
    searchTextField.setText("")
  }

  def openImdbWebpage(event: jfxe.ActionEvent) {
    java.awt.Desktop.getDesktop().browse(new java.net.URL(imdbHyperlink.getText()).toURI())
  }

  def openTmdbWebpage(event: jfxe.ActionEvent) {
    java.awt.Desktop.getDesktop().browse(new java.net.URL(tmdbHyperlink.getText()).toURI())
  }

  def updateSeenDate(event: jfxe.ActionEvent) {
    //not auto saving seenDate here since setting a date programmatically trigger ActionEvent 
  }

  def saveSeenDate(event: jfxe.ActionEvent) {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    val date = seenDatePicker.getValue()
    if (date != null)
      shelfActor ! Utils.SaveSeenDate(this, java.sql.Date.valueOf(date))
  }

}
