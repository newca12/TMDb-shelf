package org.edla.tmdb.shelf

import java.nio.file.{Files, Paths, StandardCopyOption}
import javafx.event.EventHandler
import javafx.scene.image.{Image, ImageView}
import javafx.scene.input.MouseEvent

import akka.actor.{Actor, Props, actorRef2Scala}
import akka.event.LoggingReceive
import org.edla.tmdb.api.Protocol.{Movie, noCrew}
import org.edla.tmdb.client.TmdbClient

import scala.async.Async.async
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.Try

import scala.util.{Success, Failure}

object ShelfActor {
  def props(apiKey: String, tmdbTimeOut: FiniteDuration = 5 seconds): Props =
    Props(new ShelfActor(apiKey, tmdbTimeOut))
}

object SearchMode extends Enumeration {
  val Search, Collection = Value
}

class ShelfActor(apiKey: String, tmdbTimeOut: FiniteDuration) extends Actor with akka.actor.ActorLogging {

  val PosterSize = 108.0
  val MaxItems   = 40

  val tmdbClient        = TmdbClient(apiKey, java.util.Locale.getDefault().getLanguage, tmdbTimeOut)
  @volatile var nbItems = 0
  var page: Int         = 1
  var maxPage: Int      = 1
  var items: Array[javafx.scene.image.ImageView] =
    new Array[javafx.scene.image.ImageView](MaxItems)
  var searchMode                       = SearchMode.Search
  var search: String                   = ""
  var selectedMovie: Int               = _
  var selectedCollectionFilter: Number = 0
  var selectedSearchFilter: Number     = 0

  private def refreshInfo(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Int) = {
    val releases = tmdbClient.getReleases(tmdbId)
    releases.onComplete {
      case Success(release) ⇒
        Launcher.scalaFxActor ! Utils.ShowReleases(shelf, release)
      case Failure(e) ⇒
        log.error(s"refreshInfo: Future getReleases($tmdbId) failed : ${e.getMessage}")
    }
    val credits = tmdbClient.getCredits(tmdbId)
    credits.onComplete {
      case Success(credits) ⇒
        selectedMovie = tmdbId
        Launcher.scalaFxActor ! Utils.RefreshCredits(shelf, tmdbId, credits)
      case Failure(e) ⇒
        log.error(s"refreshInfo: Future getCredits($tmdbId) failed : ${e.getMessage}")
    }
  }

  private def addToShelf(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      movie: Movie,
      poster: javafx.scene.image.Image,
      runTime: Option[Int]
  ): Unit = {
    addToShelf(
      shelf,
      movie.id,
      movie.release_date.getOrElse("Unknown"),
      movie.title,
      movie.original_title,
      movie.imdb_id.getOrElse(""),
      poster,
      runTime
    )
  }

  // scalastyle:off method.length
  private def addToShelf(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      tmdbId: Int,
      releaseDate: String,
      title: String,
      originalTitle: String,
      imdbID: String,
      poster: javafx.scene.image.Image,
      runTime: Option[Int]
  ) = {
    val ds = new javafx.scene.effect.DropShadow()
    ds.setOffsetY(-5.0)
    ds.setOffsetX(5.0)
    if (runTime.isDefined)
      if (runTime.get < 90) {
        ds.setColor(javafx.scene.paint.Color.RED)
      } else {
        if (runTime.get < 95 && runTime.get >= 90) {
          ds.setColor(javafx.scene.paint.Color.YELLOW)
        } else {
          ds.setColor(javafx.scene.paint.Color.BLACK)
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
        selectedMovie = tmdbId
        shelf.posterImageView.setImage(poster)
        Launcher.scalaFxActor ! Utils.RefreshMovieFromDb(shelf, title, originalTitle, releaseDate, imdbID)
        val movie = tmdbClient.getMovie(tmdbId)
        movie.onComplete {
          case Success(movie) ⇒
            Launcher.scalaFxActor ! Utils.RefreshMovieFromTmdb(shelf, movie)
          case Failure(e) ⇒
            log.error(s"addToShelf: Future getMovie($tmdbId) failed : ${e.getMessage}")
        }

        async {
          val q                     = Await.result(DAO.findById(tmdbId), 5 seconds)
          val (score, isTheatrical) = ImdbInfo.getInfoFromId(imdbID)
          if (q.isEmpty) {
            Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, None, "", true)
            Launcher.scalaFxActor ! Utils.RefreshScore(shelf, None, score)
          } else {
            val m = q.get
            Launcher.scalaFxActor ! Utils.RefreshScore(shelf, m.imdbScore, score)
            Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, m.viewingDate, m.comment, m.viewable)
            Launcher.scalaFxActor ! Utils.ShowRunTime(shelf, m.runTime)

          }
          if (isTheatrical.getOrElse(false)) {
            Launcher.scalaFxActor ! Utils.NotTheatricalFilmPoster(shelf, imageView_)
          }
        }
        refreshInfo(shelf, tmdbId)
        log.debug(s"event for movie $tmdbId $title")
      }
    })
    self ! Utils.AddPoster(shelf, imageView_)
  }

  // scalastyle:off cyclomatic.complexity
  def receive: PartialFunction[Any, Unit] = LoggingReceive {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case Utils.ChangePage(shelf, change) ⇒
      page = page + change
      searchMode match {
        case SearchMode.Search ⇒
          self ! Utils.Search(shelf, this.search, user = false)
        case SearchMode.Collection ⇒
          self ! Utils.ShowCollection(shelf, this.search, user = false)
      }
    case Utils.Search(shelf, search, user) ⇒
      if (user) page = 1
      searchMode = SearchMode.Search
      this.search = search
      nbItems = 0
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      val results = tmdbClient.searchMovie(search, page * 2 - 1)
      results.onComplete {
        case Success(results) ⇒
          maxPage = results.total_pages
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page, Math.ceil(maxPage / 2.0).toInt)
          for (movie ← results.results) {
            self ! Utils.GetResult(shelf, movie)
          }
          if (page < maxPage) {
            val results = tmdbClient.searchMovie(search, page * 2)
            results.onComplete {
              case Success(results) ⇒
                for (movie ← results.results) {
                  self ! Utils.GetResult(shelf, movie)
                }
              case Failure(e) ⇒
                log.error(s"ShelfActor:receive: Future searchMovie($search,${page * 2} failed : ${e.getMessage}")
            }
          }
        case Failure(e) ⇒
          log.error(s"ShelfActor:receive: Future searchMovie($search,${page * 2 -
            1} failed : ${e.getMessage}")
      }
    case "token" ⇒
      sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    case Utils.GetResult(shelf, result) ⇒
      val movie = tmdbClient.getMovie(result.id)
      movie.onComplete {
        case Success(movie) ⇒
          import java.nio.file.{Files, Paths}
          val filename = Paths.get(s"${Launcher.tmpDir}/${movie.id}.jpg")
          if (!Files.exists(filename)) {
            val f = tmdbClient.downloadPoster(movie, filename)
            if (f.isDefined) {
              f.get.onComplete {
                case Success(io) ⇒ addToShelf(shelf, movie, new Image(s"file:///$filename"), None)
                case Failure(e) ⇒
                  log.error(s"ShelfActor:receive: Future downloadPoster($movie,$filename) failed : ${e.getMessage}")
              }
            } else {
              addToShelf(
                shelf,
                movie,
                new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png"),
                None
              )
            }
          } else {
            log.debug("poster already there:" + movie.id)
            addToShelf(shelf, movie, new Image(s"file:///$filename"), None)
          }
        case Failure(e) ⇒
          log.error(s"ShelfActor:receive: Future getMovie(${result.id}) failed : ${e.getMessage}")
          e.printStackTrace()
      }
    case Utils.AddPoster(shelf, imageView) ⇒
      if (nbItems < MaxItems) {
        Launcher.scalaFxActor ! Utils.AddPosterXy(shelf, imageView, Utils.Position(nbItems % 8, nbItems / 8))
        items(nbItems) = imageView
      }
      nbItems = nbItems + 1
    case Utils.SaveMovie(shelf) ⇒
      val filename = s"${Launcher.tmpDir}/$selectedMovie.jpg"
      if (Files.exists(Paths.get(filename))) {
        Files
          .copy(Paths.get(filename), Paths.get(s"$localStore/$selectedMovie.jpg"), StandardCopyOption.REPLACE_EXISTING)
      }
      val movie = Await.result(tmdbClient.getMovie(selectedMovie), 5 seconds)
      val credits =
        Await.result(tmdbClient.getCredits(selectedMovie), 5 seconds)
      val director =
        credits.crew.find(crew ⇒ crew.job == "Director").getOrElse(noCrew).name
      val _ = async {
        try {
          val tmp = MovieDB(
            movie.id,
            java.sql.Date.valueOf(movie.release_date.getOrElse("Unknown")),
            movie.title,
            movie.original_title,
            director,
            new java.sql.Date(new java.util.Date().getTime),
            None,
            availability = true,
            //we do not want to store a movie without an imd_id so we intentionally let the exception occur
            movie.imdb_id.get,
            ImdbInfo.getScoreFromId(movie.imdb_id.get),
            seen = false,
            "",
            viewable = true,
            None //we don't want to store unchecked movie.runtime
          )
          Await.result(DAO.insert(tmp), 5 seconds)
          log.info(s"${movie.title} registered")
          refreshInfo(shelf, movie.id)
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Registered")
        } catch {
          case e: Exception ⇒
            log.error(e.getMessage)
            Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
        }
      }
    //Launcher.scalaFxActor ! Utils.RefreshCredits(shelf, movie.id, credits)
    case Utils.RemoveMovie(shelf) ⇒
      val movie = Await.result(tmdbClient.getMovie(selectedMovie), 5 seconds)
      Launcher.scalaFxActor ! Utils.ConfirmDeletion(shelf, movie)
    case Utils.DeletionConfirmed(shelf, movie) ⇒
      try {
        Await.result(DAO.delete(movie.id), 5 seconds)
        Files.deleteIfExists(Paths.get(s"$localStore/${movie.id}.jpg"))
        log.warning(s"Movie ${movie.id} ${movie.title} removed")
        Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "REMOVED")
        refreshInfo(shelf, movie.id)
      } catch {
        case e: Exception ⇒
          log.error(e.getMessage)
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }
    case Utils.ShowCollection(shelf, search, user) ⇒
      nbItems = 0
      searchMode = SearchMode.Collection
      this.search = search.toLowerCase()
      if (user) page = 1
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)

      val futureResDB = DAO.filter(selectedCollectionFilter.intValue(), selectedSearchFilter.intValue(), search)
      futureResDB
        .map { result ⇒
          maxPage = (result.size / MaxItems) + 1
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page, maxPage)
          val dropN: Int = (page - 1) * MaxItems
          result.slice(dropN, dropN + MaxItems) foreach {
            //TODO match seen true/false
            case m: MovieDB ⇒
              val filename = s"$localStore/${m.tmdbId}.jpg"
              val image =
                if (Files.exists(Paths.get(filename))) {
                  new Image(s"file:///$filename")
                } else {
                  new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png")
                }
              addToShelf(shelf, m.tmdbId, m.releaseDate.toString, m.title, m.originalTitle, m.imdbId, image, m.runTime)
          }
        }
        .recover {
          case e: Exception ⇒
            log.error(s"Problem found in ShowCollection filter process: $e")
            Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
        }
      ()

    case Utils.SaveSeenDate(shelf, seenDate) ⇒
      DAO
        .updateSeenDate(selectedMovie, seenDate)
        .map { result ⇒
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Date updated")
        }
        .recover {
          case e: Exception ⇒
            log.error(s"Problem found in updateSeenDate process: $e")
            Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
        }
      ()

    case Utils.RefreshMovie(shelf) ⇒
      DAO
        .refreshMovie(selectedMovie, shelf.commentTextArea.getText(), shelf.viewableCheckBox.isSelected)
        .map { result ⇒
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Movie updated")
        }
        .recover {
          case e: Exception ⇒
            log.error(s"Problem found in refreshMovie process: $e")
            Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
        }
      ()

    case Utils.SetCollectionFilter(shelf, filter) ⇒
      selectedCollectionFilter = filter
    case Utils.SetSearchFilter(shelf, filter) ⇒
      selectedSearchFilter = filter

    case Utils.SetRunTime(shelf) ⇒
      Launcher.scalaFxActor ! Utils.SetRunTime(shelf)

    case Utils.CheckedRunTime(shelf, runTime: Int) ⇒
      DAO
        .saveRunTime(selectedMovie, runTime)
        .map { result ⇒
          Launcher.scalaFxActor ! Utils.ShowRunTime(shelf, Some(runTime))
        }
        .recover {
          case e: Exception ⇒
            log.error(s"Problem found in CheckedRunTime process: $e")
            Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
        }
      ()

    case Utils.FindchangedScore(shelf) ⇒
      Launcher.scalaFxActor ! Utils.FindchangedScore(shelf)

    case Utils.FindchangedScoreTerminated(shelf) ⇒
      Launcher.scalaFxActor ! Utils.FindchangedScoreTerminated(shelf)

    case Utils.FoundNewScore(shelf, title) ⇒
      Launcher.scalaFxActor ! Utils.FoundNewScore(shelf, title)

    // scalastyle:on method.length
    // scalastyle:on cyclomatic.complexity
  }
}
