package org.edla.tmdb

import scalafxml.core.macros.sfxml
import scalafx.event.ActionEvent
import scalafx.scene.Scene
import scalafx.scene.control.TextField

@sfxml
class TmdbPresenter(private val apikey: TextField) {

  def check(event: ActionEvent) {

  }

  def handleClear(event: ActionEvent) {
    apikey.text = ""
  }

}