package org.edla.tmdb.shelf

import akka.actor.ActorSelection.toScala
import javafx.fxml.Initializable
import javafx.scene.{control => jfxsc, image => jfxsi, layout => jfxsl}
import javafx.{event => jfxe, fxml => jfxf}

class TmdbPresenter extends Initializable {
  //variable name are case sensitive but mistake will be detected only at runtime
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
  var runTimeButton: jfxsc.Button = _
  @jfxf.FXML
  var localizedReleaseLabel: jfxsc.Label = _
  @jfxf.FXML
  var seenDatePicker: jfxsc.DatePicker = new jfxsc.DatePicker()
  @jfxf.FXML
  var saveSeenDateButton: jfxsc.Button = _
  @jfxf.FXML
  var imdbHyperlink: jfxsc.Hyperlink =
    new jfxsc.Hyperlink("http://www.imdb.com")
  @jfxf.FXML
  var scoreLabel: jfxsc.Label = _
  @jfxf.FXML
  var scoreImageView: jfxsi.ImageView = _
  @jfxf.FXML
  var tmdbHyperlink: jfxsc.Hyperlink =
    new jfxsc.Hyperlink("http://www.themoviedb.org/")
  @jfxf.FXML
  var commentTextArea: jfxsc.TextArea = _
  @jfxf.FXML
  var viewableCheckBox: jfxsc.CheckBox = _
  @jfxf.FXML
  var menuBar: jfxsc.MenuBar = _
  @jfxf.FXML
  var logListView: jfxsc.ListView[String] = _
  @jfxf.FXML
  var progressBar: jfxsc.ProgressBar = _

  import java.net.URL
  import java.util.ResourceBundle

  import javafx.beans.value.{ChangeListener, ObservableValue}
  override def initialize(fxmlFileLocation: URL, resources: ResourceBundle): Unit = {

    menuBar.setUseSystemMenuBar(true)
    //filterCollectionChoiceBox = new jfxsc.ChoiceBox(FXCollections.observableArrayList("filter 1", "filter 2", "filter 3"))
    filterCollectionChoiceBox.getItems.addAll("Not seen", "All", "Seen", "Not available", "Not viewable")
    filterCollectionChoiceBox.getSelectionModel.selectFirst()
    filterCollectionChoiceBox.getSelectionModel
      .selectedIndexProperty()
      .addListener(
        new ChangeListener[Number]() {
          def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number): Unit = {
            val shelfActor =
              Launcher.system.actorSelection("/user/shelfactor")
            shelfActor ! Utils.SetCollectionFilter(TmdbPresenter.this, newValue)
          }
        }
      )

    filterSearchChoiceBox.getItems.addAll("All", "Director", "Movie name")
    filterSearchChoiceBox.getSelectionModel.selectFirst()
    filterSearchChoiceBox.getSelectionModel
      .selectedIndexProperty()
      .addListener(
        new ChangeListener[Number]() {
          def changed(ov: ObservableValue[_ <: Number], value: Number, newValue: Number): Unit = {
            val shelfActor =
              Launcher.system.actorSelection("/user/shelfactor")
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
    shelfActor ! Utils.ShowCollection(this, searchTextField.getText().toLowerCase(), user = true)
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
    shelfActor ! Utils.Search(this, searchTextField.getText().replace('.', ' ').toLowerCase(), user = true)
  }

  def handleClear(event: jfxe.ActionEvent): Unit = {
    searchTextField.setText("")
  }

  def openImdbWebpage(event: jfxe.ActionEvent): Unit = {
    java.awt.Desktop.getDesktop
      .browse(new java.net.URL(imdbHyperlink.getText).toURI)
  }

  def openTmdbWebpage(event: jfxe.ActionEvent): Unit = {
    java.awt.Desktop.getDesktop
      .browse(new java.net.URL(tmdbHyperlink.getText).toURI)
  }

  def updateSeenDate(event: jfxe.ActionEvent): Unit = {
    //not auto saving seenDate here since setting a date programmatically trigger ActionEvent
  }

  def saveSeenDate(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    val date       = Option(seenDatePicker.getValue)
    if (date.isDefined) {
      shelfActor ! Utils.SaveSeenDate(this, java.sql.Date.valueOf(date.get))
    }
  }

  def findFile(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.SetRunTime(this)
  }

  def menuScore(event: jfxe.ActionEvent): Unit = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    shelfActor ! Utils.FindchangedScore(this)
  }

}
