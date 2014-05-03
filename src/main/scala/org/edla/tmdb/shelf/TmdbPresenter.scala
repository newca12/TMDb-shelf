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
  check_button.onAction = { (_: ActionEvent) ⇒
    Launcher.scalaFxActor ! "reset"
    println(search.text.value)
    val tmdbClient = Utils.getTmdbClient
    val movies = Await.result(tmdbClient.searchMovie(search.text.value), 5 seconds)
    val test = movies.results
    for (m ← movies.results) {
      tmdbClient.log.info(s"find ${m.title}")
      val movie = Await.result(tmdbClient.getMovie(m.id), 5 seconds)
      movie.poster_path match {
        case Some(p) ⇒
          Await.result(tmdbClient.downloadPoster(movie, s"/tmp/${m.id}.jpg"), 5 seconds)
          Launcher.scalaFxActor ! Utils.Add(shelf, m.id)
        case None ⇒ tmdbClient.log.info("no poster")
      }
    }
  }

  def check(event: ActionEvent) {

  }

  def handleClear(event: ActionEvent) {
    search.text = ""
  }

}