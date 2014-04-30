package org.edla.tmdb.shelf

import org.edla.tmdb.client.TmdbClient
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.concurrent.Await
import akka.actor.Actor
import scala.language.postfixOps

class ShelfActor(apiKey: String) extends Actor {

  val tmdbClient = TmdbClient(apiKey, 3 seconds)

  def receive = {
    case "instance" ⇒ sender ! tmdbClient
    case "token"    ⇒ sender ! Try(Await.result(tmdbClient.getToken, 5 second).request_token)
  }

}