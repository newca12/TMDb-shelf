package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps
import akka.event.Logging
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.event.EventHandler
import javafx.scene.input.MouseEvent
import org.edla.tmdb.api._
import org.edla.tmdb.api.Protocol._
import scala.util.Success
import slick.driver.H2Driver.api._
import java.nio.file.{ Paths, Files }
import scala.async.Async.async

object ShelfActor {
  def apply(apiKey: String, tmdbTimeOut: FiniteDuration = 5 seconds) = new ShelfActor(apiKey, tmdbTimeOut)
}

object SearchMode extends Enumeration {
  val Search, Collection = Value
}

class ShelfActor(apiKey: String, tmdbTimeOut: FiniteDuration) extends Actor with akka.actor.ActorLogging {

  val tmdbClient = TmdbClient(apiKey, java.util.Locale.getDefault().getLanguage, tmdbTimeOut)
  @volatile var nbItems = 0
  var page: Long = 1
  var maxPage: Long = 1
  val maxItems = 40
  var items: Array[javafx.scene.image.ImageView] = new Array[javafx.scene.image.ImageView](maxItems)
  var searchMode = SearchMode.Search
  var search: String = ""
  var selectedMovie: Long = _
  var selectedCollectionFilter: Number = 0
  var selectedSearchFilter: Number = 0

  def refreshInfo(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Long) = {
    val releases = tmdbClient.getReleases(tmdbId)
    releases.onSuccess {
      case release ⇒
        Launcher.scalaFxActor ! Utils.ShowReleases(shelf, release)
    }
    releases.onFailure {
      case e: Exception ⇒
        log.error(s"refreshInfo: Future getReleases(${tmdbId}) failed : ${e.getMessage()}")
    }
    val credits = tmdbClient.getCredits(tmdbId)
    credits.onSuccess {
      case credits ⇒
        selectedMovie = tmdbId
        Launcher.scalaFxActor ! Utils.RefreshCredits(shelf, tmdbId, credits)
    }
    credits.onFailure {
      case e: Exception ⇒
        log.error(s"refreshInfo: Future getCredits(${tmdbId}) failed : ${e.getMessage()}")
    }
  }

  def addToShelf_(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, poster: javafx.scene.image.Image) = {
    addToShelf(shelf, movie.id, movie.release_date.toString, movie.title, movie.original_title, movie.imdb_id, poster)
  }

  def addToShelf(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Long, releaseDate: String, title: String, originalTitle: String, imdbID: String, poster: javafx.scene.image.Image) = {
    val ds = new javafx.scene.effect.DropShadow()
    ds.setOffsetY(-5.0);
    ds.setOffsetX(5.0);
    ds.setColor(javafx.scene.paint.Color.BLACK)
    var imageView_ = new ImageView()
    imageView_.setImage(poster)
    imageView_.setFitHeight(108)
    imageView_.setFitWidth(108)
    imageView_.setPreserveRatio(true)
    imageView_.setSmooth(true)
    imageView_.setEffect(ds)
    imageView_.setOnMouseClicked(new EventHandler[MouseEvent] {
      override def handle(event: MouseEvent) = {
        event.consume
        selectedMovie = tmdbId
        shelf.posterImageView.setImage(poster)
        Launcher.scalaFxActor ! Utils.RefreshMovieFromDb(shelf, title, originalTitle, releaseDate, imdbID)
        val movie = tmdbClient.getMovie(tmdbId)
        movie.onSuccess {
          case movie ⇒
            Launcher.scalaFxActor ! Utils.RefreshMovieFromTmdb(shelf, movie)
        }
        movie.onFailure {
          case e: Exception ⇒
            log.error(s"addToShelf: Future getMovie(${tmdbId}) failed : ${e.getMessage()}")
        }

        async {
          val q = Await.result(DAO.findById(tmdbId), 5 seconds)
          if (q.isEmpty) {
            val imdbInfo = ImdbInfo.getInfoFromId(imdbID)
            Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, None, "")
            Launcher.scalaFxActor ! Utils.RefreshScore(shelf, None, imdbInfo._1)
            if ((imdbInfo._2.isEmpty) || (imdbInfo._2.get)) Launcher.scalaFxActor ! Utils.TVPoster(shelf, imageView_)
          } else {
            val m = q.get
            Launcher.scalaFxActor ! Utils.RefreshScore(shelf, m.imdbScore, ImdbInfo.getScoreFromId(imdbID))
            Launcher.scalaFxActor ! Utils.ShowSeenDate(shelf, m.viewingDate, m.comment)
          }
        }
        refreshInfo(shelf, tmdbId)
        log.debug(s"event for movie ${tmdbId} ${title}")
      }
    })
    self ! Utils.AddPoster(shelf, imageView_)
  }

  def receive = {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case Utils.ChangePage(shelf, change) ⇒
      page = page + change
      searchMode match {
        case SearchMode.Search     ⇒ self ! Utils.Search(shelf, this.search, false)
        case SearchMode.Collection ⇒ self ! Utils.ShowCollection(shelf, this.search, false)
      }
    case Utils.Search(shelf, search, user) ⇒
      if (user) page = 1
      searchMode = SearchMode.Search
      this.search = search
      nbItems = 0
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)
      val results = tmdbClient.searchMovie(search, page * 2 - 1)
      results.onSuccess {
        case results ⇒
          //TODO type change needed in tmdb-async-client
          maxPage = results.total_pages.toLong
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page, Math.ceil(maxPage / 2.0).toLong)
          for (movie ← results.results) {
            tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
            self ! Utils.GetResult(shelf, movie)
          }
          if (page < maxPage) {
            val results = tmdbClient.searchMovie(search, page * 2)
            results.onSuccess {
              case results ⇒
                for (movie ← results.results) {
                  tmdbClient.log.info(s"find ${movie.id} - ${movie.title}")
                  self ! Utils.GetResult(shelf, movie)
                }
            }
            results.onFailure {
              case e: Exception ⇒
                log.error(s"ShelfActor:receive: Future searchMovie(${search},${page * 2} failed : ${e.getMessage()}")
            }
          }
      }
      results.onFailure {
        case e: Exception ⇒
          log.error(s"ShelfActor:receive: Future searchMovie(${search},${page * 2 - 1} failed : ${e.getMessage()}")
      }
    case "token" ⇒ sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    case Utils.GetResult(shelf, result) ⇒
      val movie = tmdbClient.getMovie(result.id)
      movie.onSuccess {
        case movie ⇒
          import java.nio.file.{ Paths, Files }
          val filename = s"${Launcher.tmpDir}/${movie.id}.jpg"
          if (!Files.exists(Paths.get(filename))) {
            val f = tmdbClient.downloadPoster(movie, filename)
            f.onSuccess {
              case true ⇒
                addToShelf_(shelf, movie, new Image(s"file:///${filename}"))
              case false ⇒
                addToShelf_(shelf, movie, new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png"))
            }
            f.onFailure {
              case e: Exception ⇒
                log.error(s"ShelfActor:receive: Future downloadPoster(${movie},${filename}) failed : ${e.getMessage()}")
            }
          } else {
            log.debug("poster already there:" + movie.id)
            addToShelf_(shelf, movie, new Image(s"file:///${filename}"))
          }
      }
      movie.onFailure {
        case e: Exception ⇒
          log.error(s"ShelfActor:receive: Future getMovie(${result.id}) failed : ${e.getMessage()}")
      }
    case Utils.AddPoster(shelf, imageView) ⇒
      if (nbItems < maxItems) {
        Launcher.scalaFxActor ! Utils.AddPosterXy(shelf, imageView, Utils.Position(nbItems % 8, nbItems / 8))
        items(nbItems) = imageView
      }
      nbItems = nbItems + 1
    case Utils.SaveMovie(shelf) ⇒
      val filename = s"${Launcher.tmpDir}/${selectedMovie}.jpg"
      if (Files.exists(Paths.get(filename)))
        Files.copy(Paths.get(filename), Paths.get(s"${localStore}/${selectedMovie}.jpg"))
      val movie = Await.result(tmdbClient.getMovie(selectedMovie), 5 seconds)
      val credits = Await.result(tmdbClient.getCredits(selectedMovie), 5 seconds)
      val director = credits.crew.filter(crew ⇒ crew.job == "Director").headOption.getOrElse(noCrew).name
      async {
        try {
          val tmp = MovieDB(movie.id, java.sql.Date.valueOf(movie.release_date), movie.title, movie.original_title, director,
            new java.sql.Date(new java.util.Date().getTime()), None, true, movie.imdb_id, ImdbInfo.getScoreFromId(movie.imdb_id), false, "")
          val q = Await.result(DAO.insert(tmp), 5 seconds)
          log.info(s"${movie.title} registered")
          refreshInfo(shelf, movie.id)
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Registered")
        } catch {
          case e: Exception ⇒
            log.error(e.getMessage())
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
        Files.delete(Paths.get(s"${localStore}/${movie.id}.jpg"))
        log.warning(s"Movie ${movie.id} ${movie.title} removed")
        Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "REMOVED")
        refreshInfo(shelf, movie.id)
      } catch {
        case e: Exception ⇒
          log.error(e.getMessage())
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }
    case Utils.ShowCollection(shelf, search, user) ⇒
      nbItems = 0
      searchMode = SearchMode.Collection
      this.search = search.toLowerCase()
      if (user) page = 1
      Launcher.scalaFxActor ! Utils.Reset(shelf, items.clone)

      val futureResDB = DAO.filter(selectedCollectionFilter.intValue(), selectedSearchFilter.intValue(), search)
      val resDB = futureResDB.map {
        result ⇒
          maxPage = (result.size.toLong / maxItems) + 1
          Launcher.scalaFxActor ! Utils.ShowPage(shelf, page, maxPage)
          val dropN: Int = ((page - 1) * maxItems).toInt
          result.drop(dropN).take(maxItems) foreach {
            //TODO match seen true/false
            case m: MovieDB ⇒
              val filename = s"${localStore}/${m.tmdbId}.jpg"
              val image =
                if (Files.exists(Paths.get(filename))) new Image(s"file:///${filename}")
                else new Image("/org/edla/tmdb/shelf/view/images/200px-No_image_available.svg.png")
              addToShelf(shelf, m.tmdbId, m.releaseDate.toString, m.title, m.originalTitle, m.imdbId, image)
          }
      }.recover {
        case e: Exception ⇒
          log.error("Problem found in ShowCollection filter process")
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }

    case Utils.SaveSeenDate(shelf, seenDate) ⇒

      val futureResDB = DAO.updateSeenDate(selectedMovie, seenDate).map {
        result ⇒ Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Date updated")
      }.recover {
        case e: Exception ⇒
          log.error("Problem found in updateSeenDate process")
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }

    case Utils.RefreshMovie(shelf) ⇒
      val futureResDB = DAO.refreshMovie(selectedMovie, shelf.commentTextArea.getText()).map {
        result ⇒ Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "Movie updated")
      }.recover {
        case e: Exception ⇒
          log.error("Problem found in refreshMovie process")
          Launcher.scalaFxActor ! Utils.ShowPopup(shelf, "ERROR")
      }

    case Utils.SetCollectionFilter(shelf, filter) ⇒
      selectedCollectionFilter = filter
    case Utils.SetSearchFilter(shelf, filter) ⇒
      selectedSearchFilter = filter
  }

}
