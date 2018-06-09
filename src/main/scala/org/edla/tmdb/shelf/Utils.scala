package org.edla.tmdb.shelf

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ListChangeListener
import javafx.event.{Event, EventHandler}
import javafx.scene.control.{ListCell, ListView}
import javafx.util.Callback

import akka.pattern.ask
import akka.util.Timeout
import org.edla.tmdb.api.Protocol.{Credits, Movie, Releases, Result}
import org.edla.tmdb.client.TmdbClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Utils {

  case class GetResult(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Result)
  case class Reset(shelf: org.edla.tmdb.shelf.TmdbPresenter, items: Array[javafx.scene.image.ImageView])
  case class Search(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String, user: Boolean)
  case class Position(x: Int, y: Int)
  case class AddPoster(shelf: org.edla.tmdb.shelf.TmdbPresenter, poster: javafx.scene.image.ImageView)
  case class AddPosterXy(shelf: org.edla.tmdb.shelf.TmdbPresenter, poster: javafx.scene.image.ImageView, pos: Position)
  case class NotTheatricalFilmPoster(shelf: org.edla.tmdb.shelf.TmdbPresenter, poster: javafx.scene.image.ImageView)
  case class ShowPage(shelf: org.edla.tmdb.shelf.TmdbPresenter, page: Int, maxPage: Int)
  case class RefreshMovieFromDb(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      title: String,
      original_title: String,
      release_date: String,
      imdb_id: String
  )
  case class RefreshMovieFromTmdb(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  case class RefreshCredits(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Int, crew: Credits)
  case class RefreshScore(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      imdbScore: Option[BigDecimal],
      score: Option[BigDecimal]
  )
  case class ChangePage(shelf: org.edla.tmdb.shelf.TmdbPresenter, change: Int)
  case class ShowCollection(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String, user: Boolean)
  case class SaveMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class RemoveMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class RefreshMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class ConfirmDeletion(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  case class DeletionConfirmed(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  case class ShowReleases(shelf: org.edla.tmdb.shelf.TmdbPresenter, releases: Releases)
  case class ShowSeenDate(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      seenDate: Option[java.sql.Date],
      comment: String,
      viewable: Boolean
  )
  case class SaveSeenDate(shelf: org.edla.tmdb.shelf.TmdbPresenter, seenDate: java.sql.Date)
  case class ShowPopup(shelf: org.edla.tmdb.shelf.TmdbPresenter, popup: String)
  case class SetCollectionFilter(shelf: org.edla.tmdb.shelf.TmdbPresenter, filter: Number)
  case class SetSearchFilter(shelf: org.edla.tmdb.shelf.TmdbPresenter, filter: Number)

  case class SetRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class ShowRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter, runTime: Option[Int])
  case class CheckedRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter, runTime: Int)
  case class DisableRunTimeButton(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class FindchangedScore(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class FoundNewScore(shelf: org.edla.tmdb.shelf.TmdbPresenter, title: String)
  case class FindchangedScoreTerminated(shelf: org.edla.tmdb.shelf.TmdbPresenter)

  def getTmdbClient: TmdbClient = {
    val shelfActor       = Launcher.system.actorSelection("/user/shelfactor")
    implicit val timeout = Timeout(5 seconds)
    val future: Future[TmdbClient] =
      ask(shelfActor, "instance").mapTo[TmdbClient]
    Await.result(future, 5 second)
  }
}

//http://stackoverflow.com/questions/11377350/scala-java-interop-class-type-not-converted
//https://gist.github.com/rladstaetter/5570916
trait JfxUtils {

  def mkChangeListener[T](onChangeAction: (ObservableValue[_ <: T], T, T) ⇒ Unit): ChangeListener[T] = {
    new ChangeListener[T]() {
      override def changed(observable: ObservableValue[_ <: T], oldValue: T, newValue: T) = {
        onChangeAction(observable, oldValue, newValue)
      }
    }
  }
  def mkListChangeListener[E](onChangedAction: ListChangeListener.Change[_ <: E] ⇒ Unit): ListChangeListener[E] = {
    new ListChangeListener[E] {
      def onChanged(changeItem: ListChangeListener.Change[_ <: E]): Unit = {
        onChangedAction(changeItem)
      }
    }
  }

  def mkCellFactoryCallback[T](listCellGenerator: ListView[T] ⇒ ListCell[T]): Callback[ListView[T], ListCell[T]] = {
    new Callback[ListView[T], ListCell[T]]() {
      override def call(list: ListView[T]): ListCell[T] =
        listCellGenerator(list)
    }
  }

  def mkEventHandler[E <: Event](f: E ⇒ Unit): EventHandler[E] = {
    new EventHandler[E] {
      def handle(e: E) = f(e)
    }
  }

  import java.util.Optional
  //import scala.language.implicitConversions
  implicit def fromOptional[T](optional: Optional[T]): Option[T] =
    if (optional.isPresent) {
      Some(optional.get)
    } else {
      None
    }
}
