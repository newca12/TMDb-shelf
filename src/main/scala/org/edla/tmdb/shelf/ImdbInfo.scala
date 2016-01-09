package org.edla.tmdb.shelf

import org.htmlcleaner.HtmlCleaner
import java.net.URL
import org.apache.commons.lang3.StringEscapeUtils
import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.DomSerializer
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants

object ImdbInfo extends {

  def getInfo(imdbId: String): (Option[BigDecimal], Boolean) = {
    val url = new URL(s"http://www.imdb.com/title/${imdbId}")
    val tagNode = new HtmlCleaner().clean(url)
    val doc: org.w3c.dom.Document = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val xpath = XPathFactory.newInstance().newXPath()
    val rawScore = xpath.evaluate("//div[contains(@class, 'titlePageSprite star-box-giga-star')]", doc, XPathConstants.STRING).toString
    val rawIsNotTheatricalFilm = xpath.evaluate("//div[contains(@class, 'infobar')]", doc, XPathConstants.STRING).toString
    val score = Some(BigDecimal(rawScore.trim))
    val isNotTheatricalFilm = List("TV Movie", "Video").exists { rawIsNotTheatricalFilm contains }
    (score, isNotTheatricalFilm)
  }

  def getScoreFromId(imdbId: String): Option[BigDecimal] = {
    if (imdbId.isEmpty()) None
    else getInfo(imdbId)._1
  }

  def getInfoFromId(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    if (imdbId.isEmpty()) (None, None)
    else {
      val info = getInfo(imdbId)
      (info._1, Some(info._2))
    }
  }

}