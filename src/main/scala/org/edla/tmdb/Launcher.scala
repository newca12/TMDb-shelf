package org.edla.tmdb

import scalafx.Includes.jfxParent2sfx
import scalafx.application.JFXApp
import scalafx.application.Platform
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.TextField
import scalafxml.core.DependenciesByType
import scalafxml.core.FXMLView
import scalafxml.core.macros.sfxml

@sfxml
class TmdbPresenter(
    private val apikey: TextField) {

  // Close button event handler
  def onClose(event: ActionEvent) {
    Platform.exit()
  }
}

object ScalaFXML extends JFXApp {

  val root = FXMLView(getClass.getResource("view/ApiKey.fxml"),
    new DependenciesByType(Map()))

  stage = new JFXApp.PrimaryStage() {
    title = "TMDb-shelf"
    scene = new Scene(root)

  }
}
