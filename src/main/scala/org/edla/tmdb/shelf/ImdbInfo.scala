package org.edla.tmdb.shelf

import java.io.{BufferedReader, InputStreamReader, OutputStreamWriter}
import java.net.{HttpURLConnection, URL}
import scala.util.matching.Regex

object ImdbInfo {

  private val graphqlEndpoint = "https://caching.graphql.imdb.com/"

  private val query =
    """query TitleInfo($id: ID!) {
      |  title(id: $id) {
      |    ratingsSummary {
      |      aggregateRating
      |    }
      |    titleType {
      |      id
      |      text
      |    }
      |  }
      |}""".stripMargin

  // Title types that are NOT theatrical films
  private val nonTheatricalTypes = Set(
    "tvMovie",
    "tvShort",
    "video",
    "tvEpisode",
    "tvSeries",
    "tvMiniSeries",
    "tvSpecial"
  )

  def getInfo(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    val requestBody = buildGraphQLRequest(imdbId)
    val responseJson = executeGraphQLRequest(requestBody)
    parseResponse(responseJson)
  }

  private def buildGraphQLRequest(imdbId: String): String = {
    val escapedQuery = query.replace("\n", "\\n").replace("\"", "\\\"")
    s"""{"query":"$escapedQuery","variables":{"id":"$imdbId"}}"""
  }

  private def executeGraphQLRequest(requestBody: String): String = {
    val url = new URL(graphqlEndpoint)
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]

    try {
      connection.setRequestMethod("POST")
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:107.0) Gecko/20100101 Firefox/107.0")
      connection.setDoOutput(true)

      val writer = new OutputStreamWriter(connection.getOutputStream, "UTF-8")
      writer.write(requestBody)
      writer.flush()
      writer.close()

      val reader = new BufferedReader(new InputStreamReader(connection.getInputStream, "UTF-8"))
      val response = new StringBuilder
      var line: String = reader.readLine()
      while (line != null) {
        response.append(line)
        line = reader.readLine()
      }
      reader.close()

      response.toString
    } finally {
      connection.disconnect()
    }
  }

  private def parseResponse(json: String): (Option[BigDecimal], Option[Boolean]) = {
    // Simple JSON parsing without external library
    val ratingPattern: Regex = """"aggregateRating"\s*:\s*([0-9.]+)""".r
    val titleTypePattern: Regex = """"titleType"\s*:\s*\{[^}]*"id"\s*:\s*"([^"]+)"""".r

    val rating = ratingPattern.findFirstMatchIn(json).map { m =>
      BigDecimal(m.group(1))
    }

    val titleType = titleTypePattern.findFirstMatchIn(json).map(_.group(1))
    val isNotTheatricalFilm = titleType.map(t => nonTheatricalTypes.contains(t))

    (rating, isNotTheatricalFilm)
  }

  def getScoreFromId(imdbId: String): Option[BigDecimal] = {
    if (imdbId.isEmpty) {
      None
    } else {
      getInfo(imdbId)._1
    }
  }

  def getInfoFromId(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    if (imdbId.isEmpty) {
      (None, None)
    } else {
      getInfo(imdbId)
    }
  }
}
