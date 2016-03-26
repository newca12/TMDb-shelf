package org.edla.tmdb.shelf

import akka.actor.ActorSelection.toScala
import javafx.{ event ⇒ jfxe, fxml ⇒ jfxf }
import javafx.fxml.Initializable
import javafx.scene.{ control ⇒ jfxsc, image ⇒ jfxsi, layout ⇒ jfxsl }

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

  import java.net.URL
  import java.util.ResourceBundle
  import javafx.beans.value.ChangeListener
  import javafx.beans.value.ObservableValue
  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {
    //filterCollectionChoiceBox = new jfxsc.ChoiceBox(FXCollections.observableArrayList("filter 1", "filter 2", "filter 3"))
    filterCollectionChoiceBox.getItems().addAll("Not seen", "All", "Seen", "Not available")
    filterCollectionChoiceBox.getSelectionModel().selectFirst()
    filterCollectionChoiceBox.getSelectionModel().selectedIndexProperty().addListener(
      new ChangeListener[Number]() {
        def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number) = {
          val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          shelfActor ! Utils.SetCollectionFilter(TmdbPresenter.this, newValue)
        }
      }
    )

    filterSearchChoiceBox.getItems().addAll("All", "Director", "Movie name")
    filterSearchChoiceBox.getSelectionModel().selectFirst()
    filterSearchChoiceBox.getSelectionModel().selectedIndexProperty().addListener(
      new ChangeListener[Number]() {
        def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number) = {
          val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
          shelfActor ! Utils.SetSearchFilter(TmdbPresenter.this, newValue)
        }
      }
    )
  }

  @jfxf.FXML
  def previousPage(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ChangePage(this, -1)
  }

  def nextPage(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ChangePage(this, 1)
  }

  def showCollection(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.ShowCollection(this, searchTextField.getText().toLowerCase(), true)
  }

  def addMovie(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.SaveMovie(this)
  }

  def refreshMovie(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.RefreshMovie(this)
  }

  def deleteMovie(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.RemoveMovie(this)
  }

  def search(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.Search(this, searchTextField.getText().replace('.', ' ').toLowerCase(), true)
  }

  def handleClear(event: jfxe.ActionEvent): Unit = {
    searchTextField.setText("")
  }

  def openImdbWebpage(event: jfxe.ActionEvent): Unit = {
    java.awt.Desktop.getDesktop().browse(new java.net.URL(imdbHyperlink.getText()).toURI())
  }

  def openTmdbWebpage(event: jfxe.ActionEvent): Unit = {
    java.awt.Desktop.getDesktop().browse(new java.net.URL(tmdbHyperlink.getText()).toURI())
  }

  def updateSeenDate(event: jfxe.ActionEvent): Unit = {
    //not auto saving seenDate here since setting a date programmatically trigger ActionEvent
  }

  def saveSeenDate(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    val date = Option(seenDatePicker.getValue())
    if (date.isDefined) {
      shelfActor ! Utils.SaveSeenDate(this, java.sql.Date.valueOf(date.get))
    }
  }

}
