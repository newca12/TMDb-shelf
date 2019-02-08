package org.edla.tmdb.shelf

import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ListChangeListener
import javafx.event.{Event, EventHandler}
import javafx.scene.control.{ListCell, ListView}
import javafx.util.Callback
import akka.pattern.ask
import akka.util.Timeout
import javafx.scene.image.ImageView
import org.edla.tmdb.api.Protocol.{Credits, Movie, Releases, Result}
import org.edla.tmdb.client.TmdbClient

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object Utils {

  final case class GetResult(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Result)
  final case class Reset(shelf: org.edla.tmdb.shelf.TmdbPresenter, items: Array[javafx.scene.image.ImageView])
  final case class Search(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String, user: Boolean)
  final case class Position(x: Int, y: Int)
  final case class AddPoster(shelf: org.edla.tmdb.shelf.TmdbPresenter, poster: javafx.scene.image.ImageView)
  final case class AddPosterXy(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      poster: javafx.scene.image.ImageView,
      pos: Position
  )
  final case class NotTheatricalFilmPoster(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      poster: javafx.scene.image.ImageView
  )
  final case class ShowPage(shelf: org.edla.tmdb.shelf.TmdbPresenter, page: Int, maxPage: Int)
  final case class RefreshMovieFromDb(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      title: String,
      original_title: String,
      release_date: String,
      imdb_id: String
  )
  final case class RefreshMovieFromTmdb(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  final case class RefreshCredits(shelf: org.edla.tmdb.shelf.TmdbPresenter, tmdbId: Int, crew: Credits)
  final case class RefreshScore(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      imdbScore: Option[BigDecimal],
      score: Option[BigDecimal]
  )
  final case class ChangePage(shelf: org.edla.tmdb.shelf.TmdbPresenter, change: Int)
  final case class ShowCollection(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String, user: Boolean)
  final case class SaveMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class RemoveMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class RefreshMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class ConfirmDeletion(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  final case class DeletionConfirmed(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie)
  final case class ShowReleases(shelf: org.edla.tmdb.shelf.TmdbPresenter, releases: Releases)
  final case class ShowSeenDate(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      seenDate: Option[java.sql.Date],
      comment: String,
      viewable: Boolean
  )
  final case class SaveSeenDate(shelf: org.edla.tmdb.shelf.TmdbPresenter, seenDate: java.sql.Date)
  final case class ShowPopup(shelf: org.edla.tmdb.shelf.TmdbPresenter, popup: String)
  final case class SetCollectionFilter(shelf: org.edla.tmdb.shelf.TmdbPresenter, filter: Number)
  final case class SetSearchFilter(shelf: org.edla.tmdb.shelf.TmdbPresenter, filter: Number)

  final case class SetRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class ShowRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter, runTime: Option[Int])
  final case class CheckedRunTime(shelf: org.edla.tmdb.shelf.TmdbPresenter, runTime: Int)
  final case class DisableRunTimeButton(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class FindchangedScore(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class FoundNewScore(shelf: org.edla.tmdb.shelf.TmdbPresenter, title: String)
  final case class InitScoreProgress(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class FoundScore(shelf: org.edla.tmdb.shelf.TmdbPresenter, progress: Float)
  final case class FindchangedScoreTerminated(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  final case class AddToShelf(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      movie: Movie,
      poster: javafx.scene.image.Image,
      runTime: Option[Int]
  )
  final case class AddToShelf2(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      tmdbId: Int,
      releaseDate: String,
      title: String,
      originalTitle: String,
      imdbID: String,
      poster: javafx.scene.image.Image,
      runTime: Option[Int]
  )
  final case class ImageViewClicked(
      shelf: org.edla.tmdb.shelf.TmdbPresenter,
      tmdbId: Int,
      imdbID: String,
      imageView: ImageView
  )

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

  def mkChangeListener[T](onChangeAction: (ObservableValue[_ <: T], T, T) => Unit): ChangeListener[T] = {
    new ChangeListener[T]() {
      override def changed(observable: ObservableValue[_ <: T], oldValue: T, newValue: T) = {
        onChangeAction(observable, oldValue, newValue)
      }
    }
  }
  def mkListChangeListener[E](onChangedAction: ListChangeListener.Change[_ <: E] => Unit): ListChangeListener[E] = {
    new ListChangeListener[E] {
      def onChanged(changeItem: ListChangeListener.Change[_ <: E]): Unit = {
        onChangedAction(changeItem)
      }
    }
  }

  def mkCellFactoryCallback[T](listCellGenerator: ListView[T] => ListCell[T]): Callback[ListView[T], ListCell[T]] = {
    new Callback[ListView[T], ListCell[T]]() {
      override def call(list: ListView[T]): ListCell[T] =
        listCellGenerator(list)
    }
  }

  def mkEventHandler[E <: Event](f: E => Unit): EventHandler[E] = {
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
