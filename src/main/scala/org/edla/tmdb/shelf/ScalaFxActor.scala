package org.edla.tmdb.shelf

import java.io.File

import akka.actor.{Actor, actorRef2Scala}
import akka.event.LoggingReceive
import javafx.event.EventHandler
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.{Alert, ButtonType}
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent
import javafx.stage.Modality
import org.edla.tmdb.api.Protocol.{Movie, noCrew, unReleased}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.math.BigDecimal.int2bigDecimal
import scala.sys.process._

class ScalaFxActor extends Actor {

  val PosterSize = 108.0

  def receive: PartialFunction[Any, Unit] = LoggingReceive {

    case Utils.AddToShelf(
        shelf: org.edla.tmdb.shelf.TmdbPresenter,
        movie: Movie,
        poster: javafx.scene.image.Image,
        runTime: Option[Int]
        ) =>
      self ! Utils.AddToShelf2(
        shelf,
        movie.id,
        movie.release_date.getOrElse("Unknown"),
        movie.title,
        movie.original_title,
        movie.imdb_id.getOrElse(""),
        poster,
        runTime
      )

    // scalastyle:off method.length
    case Utils.AddToShelf2(
        shelf: org.edla.tmdb.shelf.TmdbPresenter,
        tmdbId: Int,
        releaseDate: String,
        title: String,
        originalTitle: String,
        imdbID: String,
        poster: javafx.scene.image.Image,
        runTime: Option[Int]
        ) =>
      val ds = new javafx.scene.effect.DropShadow()
      ds.setOffsetY(-5.0)
      ds.setOffsetX(5.0)
      if (runTime.isDefined) {
        if (runTime.get < 75) {
          ds.setColor(javafx.scene.paint.Color.PURPLE)
        } else if (runTime.get < 90 && runTime.get >= 75) {
          ds.setColor(javafx.scene.paint.Color.RED)
        } else {
          if (runTime.get < 95 && runTime.get >= 90) {
            ds.setColor(javafx.scene.paint.Color.YELLOW)
          } else {
            ds.setColor(javafx.scene.paint.Color.BLACK)
          }
        }
      } else {
        ds.setColor(javafx.scene.paint.Color.WHITE)
      }
      val imageView_ = new ImageView()
      imageView_.setImage(poster)
      imageView_.setFitHeight(PosterSize)
      imageView_.setFitWidth(PosterSize)
      imageView_.setPreserveRatio(true)
      imageView_.setSmooth(true)
      imageView_.setEffect(ds)
      imageView_.setOnMouseClicked(new EventHandler[MouseEvent] {
        override def handle(event: MouseEvent) = {
          event.consume()
          //selectedMovie = tmdbId
          shelf.posterImageView.setImage(poster)
          Launcher.scalaFxActor ! Utils.RefreshMovieFromDb(shelf, title, originalTitle, releaseDate, imdbID)
          Launcher.system.actorSelection("/user/shelfactor") ! Utils.ImageViewClicked(shelf, tmdbId, imdbID, imageView_)

        }
      })
      Launcher.system.actorSelection("/user/shelfactor") ! Utils.AddPoster(shelf, imageView_)

    // scalastyle:off cyclomatic.complexity
    // scalastyle:off method.length

    case Utils.NotTheatricalFilmPoster(shelf, poster) =>
      val effect = new javafx.scene.effect.Shadow()
      effect.setColor(javafx.scene.paint.Color.BEIGE)
      //poster.setEffect(effect)
      poster.setOpacity(0.2)

    case Utils.Reset(shelf, items) =>
      for (item <- items) shelf.shelfGridPane.getChildren.remove(item)

    case Utils.AddPosterXy(shelf, poster, pos) =>
      shelf.shelfGridPane.add(poster, pos.x, pos.y)
      javafx.scene.layout.GridPane.setHalignment(poster, javafx.geometry.HPos.CENTER)

    case Utils.ShowPage(shelf, page, maxPage) =>
      shelf.pageLabel.setText(s"$page/$maxPage")
      shelf.nextButton.setDisable(page >= maxPage)
      shelf.previousButton.setDisable(page <= 1)

    case Utils.ShowReleases(shelf, releases) =>
      val release = releases.countries
        .find(country => country.iso_3166_1 == java.util.Locale.getDefault().getCountry)
        .getOrElse(unReleased)
        .release_date
      shelf.localizedReleaseLabel.setText(if (release != "Unknown") release else "Localized release")

    case Utils.ShowSeenDate(shelf, seenDate, comment, viewable) =>
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
    case Utils.RefreshMovieFromDb(shelf, title, original_title, release_date, imdb_id) =>
      shelf.titleLabel.setText(title)
      shelf.originalTitleLabel.setText(original_title)
      shelf.releaseLabel.setText(if (release_date != "") release_date else "Release")
      shelf.imdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(imdb_id))
      shelf.imdbHyperlink.setText(s"http://www.imdb.com/title/$imdb_id")

    case Utils.RefreshMovieFromTmdb(shelf, movie) =>
      shelf.runTimeButton.setText(movie.runtime match {
        case Some(runtime) => runtime.toString
        case None          => "Runtime"
      })

    case Utils.RefreshCredits(shelf, tmdbId, credits) =>
      val director =
        credits.crew.find(crew => crew.job == "Director").getOrElse(noCrew).name
      shelf.directorLabel.setText(if (director != "Unknown") director else "Director")
      shelf.tmdbHyperlink.setTooltip(new javafx.scene.control.Tooltip(tmdbId.toString))
      shelf.tmdbHyperlink.setText(s"http://www.themoviedb.org/movie/$tmdbId")

      val res = Await.result(DAO.findById(tmdbId), 5 seconds)
      shelf.addMovieButton.setDisable(res.isDefined)
      shelf.runTimeButton.setDisable(res.isDefined && res.get.runTime.isDefined)
      shelf.deleteMovieButton.setDisable(res.isEmpty)
      shelf.refreshMovieButton.setDisable(res.isEmpty)

    case Utils.RefreshScore(shelf, imdbScore, score) =>
      if (imdbScore.isDefined && score.isDefined) {
        val diff: BigDecimal = (score.get - imdbScore.get) * 10
        diff.intValue match {
          case 0 =>
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/equal-sign-2-16.png"))
          case 1 =>
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/up.png"))
          case -1 =>
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/down.png"))
          case x if x < -1 =>
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/downdown.png"))
          case x if x > 1 =>
            shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/upup.png"))
        }
      } else {
        shelf.scoreImageView.setImage(new Image("/org/edla/tmdb/shelf/view/images/equal-sign-2-16.png"))
      }
      shelf.scoreLabel.setText(score match {
        case Some(score) => score.toString
        case None        => "N/A"
      })

    case Utils.ShowPopup(shelf, msg) =>
      val label = new javafx.scene.control.Label(msg)
      new javafx.stage.Popup() {
        getContent.add(label)
        setAutoHide(true)
        setX(Launcher.stage.getX + Launcher.stage.getWidth - 110)
        setY(Launcher.stage.getY + Launcher.stage.getHeight - 40)
      }.show(Launcher.stage)

    case Utils.ConfirmDeletion(shelf, movie) =>
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

    case Utils.SetRunTime(shelf) =>
      import javafx.stage.FileChooser
      val fileChooser: FileChooser = new FileChooser
      fileChooser.setTitle("Open Resource File")
      val selectedFile: File = fileChooser.showOpenDialog(Launcher.stage)
      // scalastyle:off null
      if (selectedFile != null) {
        // scalastyle:on null
        // ffmpeg could also be used conveniently: ffprobe -i <file> -show_entries format=duration -v quiet -of csv="p=0"
        val cmd      = Seq("/usr/local/bin/mediainfo", "--Inform=General;%Duration%", selectedFile.getCanonicalPath)
        val duration = cmd.!!.trim
        if (!duration.isEmpty) sender ! Utils.CheckedRunTime(shelf, duration.toInt.millis.toMinutes.toInt)
      }

    case Utils.DisableRunTimeButton(shelf) =>
      shelf.runTimeButton.setDisable(true)

    case Utils.ShowRunTime(shelf, runTime: Option[Int]) =>
      if (runTime.isDefined) {
        shelf.runTimeButton.setDisable(true)
        shelf.runTimeButton.setText(s"${runTime.get.toString} min")
      } else {
        shelf.runTimeButton.setDisable(false)
      }

    case Utils.FindchangedScore(shelf) =>
      shelf.logListView.setDisable(false)
      ()

    case Utils.FindchangedScoreTerminated(shelf) =>
      shelf.logListView.setDisable(true)
      ()

    case Utils.FoundNewScore(shelf, title) =>
      shelf.logListView.getItems().add(title)
      ()

    case Utils.FoundScore(shelf, progress) =>
      shelf.progressBar.setProgress(progress)
  }
  // scalastyle:on method.length
  // scalastyle:on cyclomatic.complexity
}
