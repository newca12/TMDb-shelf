package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps
import akka.event.Logging

class ShelfActor(apiKey: String) extends Actor with akka.actor.ActorLogging {

  val tmdbClient = TmdbClient(apiKey, 3 seconds)

  def receive = {
    case "instance" ⇒
      log.info("instance asked")
      sender ! tmdbClient
    case "token" ⇒ sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
    case "test" ⇒
      log.info("test asked")
    case Utils.AddResult(shelf, result) ⇒
      val movie = Await.result(tmdbClient.getMovie(result.id), 5 seconds)
      movie.poster_path match {
        case Some(p) ⇒
          import java.nio.file.{ Paths, Files }
          val filename = s"${Launcher.localStore}/${result.id}.jpg"
          if (!Files.exists(Paths.get(filename)))
            Await.result(tmdbClient.downloadPoster(movie, filename), 5 seconds)
        case None ⇒
          log.debug("no poster")
      }
      Launcher.scalaFxActor ! Utils.AddMovie(shelf, movie)
  }

}
