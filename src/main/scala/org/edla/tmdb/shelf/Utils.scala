package org.edla.tmdb.shelf

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import org.edla.tmdb.api.Protocol.Movie
import org.edla.tmdb.api.Protocol.Result
import org.edla.tmdb.client.TmdbClient

import akka.pattern.ask
import akka.util.Timeout

object Utils {

  case class GetResult(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Result)
  case class AddMovie(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, imageView: scalafx.scene.image.ImageView)
  case class Reset(shelf: org.edla.tmdb.shelf.TmdbPresenter, items: Array[scalafx.scene.image.ImageView])
  case class Search(shelf: org.edla.tmdb.shelf.TmdbPresenter, search: String, page: Long)
  case class Position(x: Int, y: Int)
  case class AddPoster(shelf: org.edla.tmdb.shelf.TmdbPresenter, movie: Movie, poster: scalafx.scene.image.ImageView, pos: Position)
  case class ShowItem(shelf: org.edla.tmdb.shelf.TmdbPresenter, item: String)

  def getTmdbClient = {
    val shelfActor = Launcher.system.actorSelection("/user/shelfactor")
    implicit val timeout = Timeout(5 seconds)
    val future: Future[TmdbClient] = ask(shelfActor, "instance").mapTo[TmdbClient]
    Await.result(future, 5 second)
  }
}