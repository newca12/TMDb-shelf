package org.edla.tmdb.shelf

import javafx.scene.control.{Alert, ButtonType}
import javafx.scene.control.Alert.AlertType
import javafx.scene.image.Image
import javafx.stage.Modality

import akka.actor.{Actor, actorRef2Scala}
import akka.event.LoggingReceive
import org.edla.tmdb.api.Protocol.{noCrew, unReleased}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.BigDecimal.int2bigDecimal

class ScalaFxActor extends Actor {

  // scalastyle:off cyclomatic.complexity
  // scalastyle:off method.length
  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case Utils.NotTheatricalFilmPoster(shelf, poster) ⇒
      val effect = new javafx.scene.effect.Shadow()
      effect.setColor(javafx.scene.paint.Color.BEIGE)
      //poster.setEffect(effect)
      poster.setOpacity(0.2)

    case Utils.Reset(shelf, items) ⇒
      for (item ← items) shelf.shelfGridPane.getChildren.remove(item)

    case Utils.AddPosterXy(shelf, poster, pos) ⇒
      shelf.shelfGridPane.add(poster, pos.x, pos.y)
      javafx.scene.layout.GridPane.setHalignment(poster, javafx.geometry.HPos.CENTER)

    case Utils.ShowPage(shelf, page, maxPage) ⇒
      shelf.pageLabel.setText(s"$page/$maxPage")
      shelf.nextButton.setDisable(page >= maxPage)
      shelf.previousButton.setDisable(page <= 1)

    case Utils.ShowReleases(shelf, releases) ⇒
      val release = releases.countries
        .find(country ⇒ country.iso_3166_1 == java.util.Locale.getDefault().getCountry)
        .getOrElse(unReleased)
        .release_date
      shelf.localizedReleaseLabel.setText(if (release != "Unknown") release else "Localized release")

    case Utils.ShowSeenDate(shelf, seenDate, comment, viewable) ⇒
      shelf.commentTextArea.setText(comment)
      shelf.viewableCheckBox.setSelected(viewable)
      if (seenDate.isDefined) {
        shelf.seenDatePicker.setValue(seenDate.get.toLocalDate)
      } else {
        //shelf.seenDatePicker = new javafx.scene.control.DatePicker()
        // scalastyle:off null
        shelf.seenDatePicker.setValue(null)
        // scalastyle:on null
      }
    case Utils.RefreshMovieFromDb(shelf, title, original_title, release_date, imdb_id) ⇒
      shelf.titleLabel.setText(title)
      shelf.originalTitleLabel.setText(original_title)
      shelf.releaseLabel.setText(if (release_date != "") release_date else "Release")
      shelf.imdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(imdb_id))
      shelf.imdbHyperlink.setText(s"http://www.imdb.com/title/$imdb_id")

    case Utils.RefreshMovieFromTmdb(shelf, movie) ⇒
      val runtime = movie.runtime.getOrElse(0.toLong).toString
      shelf.runtimeLabel.setText(if (runtime != "0") runtime + " min" else "Runtime")

    case Utils.RefreshCredits(shelf, tmdbId, credits) ⇒
      val director =
        credits.crew.find(crew ⇒ crew.job == "Director").getOrElse(noCrew).name
      shelf.directorLabel.setText(if (director != "Unknown") director else "Director")
      shelf.tmdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(tmdbId.toString))
      shelf.tmdbHyperlink.setText(s"http://www.themoviedb.org/movie/$tmdbId")

      val res = Await.result(DAO.findById(tmdbId), 5 seconds)
      shelf.addMovieButton.setDisable(res.isDefined)
      shelf.deleteMovieButton.setDisable(res.isEmpty)
      shelf.refreshMovieButton.setDisable(res.isEmpty)

    case Utils.RefreshScore(shelf, imdbScore, score) ⇒
      if (imdbScore.isDefined && score.isDefined) {
        val diff = ((score.get - imdbScore.get) * 10).intValue()
        diff match {
          case 0 ⇒
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/equal-sign-2-16.png"))
          case 1 ⇒
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/up.png"))
          case -1 ⇒
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/down.png"))
          case x if x < -1 ⇒
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/downdown.png"))
          case x if x > 1 ⇒
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/upup.png"))
        }
      } else {
        shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/equal-sign-2-16.png"))
      }
      shelf.scoreLabel.setText(score.getOrElse("N/A").toString)

    case Utils.ShowPopup(shelf, msg) ⇒
      val label = new javafx.scene.control.Label(msg)
      new javafx.stage.Popup() {
        getContent.add(label)
        setAutoHide(true)
        setX(Launcher.stage.getX + Launcher.stage.getWidth - 110)
        setY(Launcher.stage.getY + Launcher.stage.getHeight - 40)
      }.show(Launcher.stage)

    case Utils.ConfirmDeletion(shelf, movie) ⇒
      val result = new Alert(AlertType.CONFIRMATION) {
        initOwner(Launcher.stage)
        initModality(Modality.APPLICATION_MODAL)
        setTitle("Confirmation needed")
        // scalastyle:off null
        setHeaderText(null)
        // scalastyle:on null
        setContentText(s"Do you really wan't to remove ${movie.title} ?")
      }.showAndWait()
      if (result.isPresent && result.get() == ButtonType.OK) {
        sender ! Utils.DeletionConfirmed(shelf, movie)
      }
  }
  // scalastyle:on method.length
  // scalastyle:on cyclomatic.complexity
}
