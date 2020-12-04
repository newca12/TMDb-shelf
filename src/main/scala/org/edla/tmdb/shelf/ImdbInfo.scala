package org.edla.tmdb.shelf

import java.net.URL
import javax.xml.xpath.{XPathConstants, XPathFactory}

import org.htmlcleaner.{CleanerProperties, DomSerializer, HtmlCleaner}

object ImdbInfo {

  def getInfo(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    val url                       = new URL(s"https://www.imdb.com/title/$imdbId")
    val tagNode                   = new HtmlCleaner().clean(url)
    val doc: org.w3c.dom.Document = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val xpath                     = XPathFactory.newInstance().newXPath()
    val rawScore = xpath
      .evaluate(
        "//div[@id='main_top']//div[@class='imdbRating']/div[@class='ratingValue']/strong",
        doc,
        XPathConstants.STRING
      )
      .toString
    val rawIsNotTheatricalFilm = xpath
      .evaluate(
        "//div[@id='main_top']//div[@class='subtext']",
        doc,
        XPathConstants.STRING
      )
      .toString
    val score =
      if (rawScore.isEmpty) {
        None
      } else {
        Some(BigDecimal(rawScore.trim.replace(",", ".")))
      }
    val isNotTheatricalFilm = Some(
      List("TV Movie", "TV Short", "Video", "Episode aired", "TV Series", "TV Special").exists {
        rawIsNotTheatricalFilm.contains
      }
    )
    (score, isNotTheatricalFilm)
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
