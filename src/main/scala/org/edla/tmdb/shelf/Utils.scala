package org.edla.tmdb.shelf

import scala.concurrent.Await
import org.edla.tmdb.client.TmdbClient
import akka.util.Timeout
import scala.concurrent.Future
import akka.pattern.ask
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Utils {

  case class AddResult(shelf: scalafx.scene.layout.GridPane, movie: org.edla.tmdb.api.Result)
  case class AddMovie(shelf: scalafx.scene.layout.GridPane, movie: org.edla.tmdb.api.Movie) //, image: scalafx.scene.image.Image)
  case class Reset(shelf: scalafx.scene.layout.GridPane)

  def getTmdbClient = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    implicit val timeout = Timeout(5 seconds)
    val future: Future[TmdbClient] = ask(shelfActor, "instance").mapTo[TmdbClient]
    Await.result(future, 5 second)
  }
}