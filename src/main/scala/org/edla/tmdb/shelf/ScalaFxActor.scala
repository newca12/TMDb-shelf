package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps
import scala.concurrent._
import ExecutionContext.Implicits.global
import org.edla.tmdb.api.Protocol._

class ScalaFxActor extends Actor {

  import javafx.scene.image.Image
  import javafx.scene.image.ImageView
  import javafx.event.ActionEvent
  import javafx.event.EventHandler
  import javafx.scene.input.MouseEvent

  def receive = {
    case Utils.Reset(shelf, items) ⇒
      for (item ← items)
        shelf.shelfGridPane.getChildren().remove(item)

    case Utils.AddPoster(shelf, movie, poster, pos) ⇒
      shelf.shelfGridPane.add(poster, pos.x, pos.y)

    case Utils.ShowPage(shelf, page) ⇒
      shelf.pageLabel.setText(page)

    case Utils.RefreshDetails(shelf, movie, credits) ⇒
      import scala.slick.driver.H2Driver.simple._
      val director = credits.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
      shelf.directorLabel.setText(director)
      Persist.db.withSession { implicit session ⇒
        val res = Persist.movies.filter(_.id === movie.id).list
        shelf.addMovieButton.setDisable(!res.isEmpty)
      }
  }

}