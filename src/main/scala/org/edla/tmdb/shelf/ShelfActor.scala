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
  }

}