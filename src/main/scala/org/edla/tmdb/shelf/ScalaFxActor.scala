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
import org.controlsfx.dialog.Dialogs
import org.controlsfx.dialog.Dialog

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

    case Utils.AddPosterXy(shelf, poster, pos) ⇒
      shelf.shelfGridPane.add(poster, pos.x, pos.y)
      javafx.scene.layout.GridPane.setHalignment(poster, javafx.geometry.HPos.CENTER)

    case Utils.ShowPage(shelf, page) ⇒
      shelf.pageLabel.setText(page)

    case Utils.ShowReleases(shelf, releases) ⇒
      val release = releases.countries.filter(
        country ⇒ country.iso_3166_1 == java.util.Locale.getDefault().getCountry).headOption.getOrElse(unReleased).release_date
      shelf.localizedReleaseLabel.setText(if (release != "") release else "Localized release")

    case Utils.ShowSeenDate(shelf, seenDate) ⇒
      if (seenDate.isDefined)
        shelf.seenDatePicker.setValue(seenDate.get.toLocalDate())
      else
        //shelf.seenDatePicker = new javafx.scene.control.DatePicker()
        shelf.seenDatePicker.setValue(null)

    case Utils.RefreshMovie(shelf, title, original_title, release_date, imdb_id) ⇒
      shelf.titleLabel.setText(title)
      shelf.originalTitleLabel.setText(original_title)
      shelf.releaseLabel.setText(release_date)
      shelf.imdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(imdb_id))
      shelf.imdbHyperlink.setText(s"http://www.imdb.com/title/${imdb_id}")

    case Utils.RefreshCredits(shelf, tmdbId, credits) ⇒
      import scala.slick.driver.H2Driver.simple._
      val director = credits.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
      shelf.directorLabel.setText(director)
      shelf.tmdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(tmdbId.toString))
      shelf.tmdbHyperlink.setText(s"http://www.themoviedb.org/movie/${tmdbId}")

      Store.db.withSession { implicit session ⇒
        val res = Store.movies.filter(_.tmdbId === tmdbId).list
        shelf.addMovieButton.setDisable(!res.isEmpty)
        shelf.deleteMovieButton.setDisable(res.isEmpty)
      }

    case Utils.RefreshScore(shelf, score) ⇒
      shelf.scoreLabel.setText(score)

    case Utils.ShowPopup(shelf, msg) ⇒
      val popup = new javafx.stage.Popup()
      val label = new javafx.scene.control.Label(msg)
      popup.getContent().add(label)
      popup.setAutoHide(true)
      popup.setX(Launcher.stage.getX() + Launcher.stage.getWidth() - 110)
      popup.setY(Launcher.stage.getY() + Launcher.stage.getHeight() - 40)
      popup.show(Launcher.stage)

    case Utils.ConfirmDeletion(shelf, movie) ⇒
      val confirmation = Dialogs.create()
        .owner(null)
        .title("Confirmation needed")
        .message(s"Do you really wan't to remove ${movie.title} ?")
        .showConfirm()
      if (confirmation == Dialog.Actions.YES) sender ! Utils.DeletionConfirmed(shelf, movie)

  }

}