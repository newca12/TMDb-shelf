package org.edla.tmdb.shelf

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.edla.tmdb.api.Protocol.Movie
import org.edla.tmdb.api.Protocol.Credits
import org.edla.tmdb.api.Protocol.Releases
import org.edla.tmdb.api.Protocol.Result
import org.edla.tmdb.client.TmdbClient

import akka.pattern.ask
import akka.util.Timeout

import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.event.Event
import javafx.event.EventHandler
import javafx.util.Callback
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView

object Utils {

  case class GetResult(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Result)
  case class AddMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, imageView: javafx.scene.image.ImageView)
  case class Reset(shelf: org.edla.tmdb.shelf.TmdbPresenter, items: Array[javafx.scene.image.ImageView])
  case class Search(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String)
  case class Position(x: Int, y: Int)
  case class AddPoster(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, poster: javafx.scene.image.ImageView, pos: Position)
  case class ShowPage(shelf: org.edla.tmdb.shelf.TmdbPresenter, page: String)
  case class RefreshDetails(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, crew: Credits)
  case class ChangePage(shelf: org.edla.tmdb.shelf.TmdbPresenter, change: Long)
  case class ShowCollection(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class SaveMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter)
  case class ShowReleases(shelf: org.edla.tmdb.shelf.TmdbPresenter, releases: Releases)
  case class ShowSeenDate(shelf: org.edla.tmdb.shelf.TmdbPresenter, seenDate: Option[java.sql.Date])
  case class SaveSeenDate(shelf: org.edla.tmdb.shelf.TmdbPresenter, seenDate: java.sql.Date)

  def getTmdbClient = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    implicit val timeout = Timeout(5 seconds)
    val future: Future[TmdbClient] = ask(shelfActor, "instance").mapTo[TmdbClient]
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
  def mkListChangeListener[E](onChangedAction: ListChangeListener.Change[_ <: E] ⇒ Unit) = new ListChangeListener[E] {
    def onChanged(changeItem: ListChangeListener.Change[_ <: E]): Unit = {
      onChangedAction(changeItem)
    }
  }

  def mkCellFactoryCallback[T](listCellGenerator: ListView[T] ⇒ ListCell[T]) = new Callback[ListView[T], ListCell[T]]() {
    override def call(list: ListView[T]): ListCell[T] = listCellGenerator(list)
  }

  def mkCellFactoryCallback_[T](listCellGenerator: GridView[T] ⇒ GridCell[T]) = new Callback[GridView[T], GridCell[T]]() {
    override def call(list: GridView[T]): GridCell[T] = listCellGenerator(list)
  }

  def mkEventHandler[E <: Event](f: E ⇒ Unit) = new EventHandler[E] { def handle(e: E) = f(e) }

}