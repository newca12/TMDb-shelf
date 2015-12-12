package org.edla.tmdb.shelf

import org.htmlcleaner.HtmlCleaner
import java.net.URL
import org.apache.commons.lang3.StringEscapeUtils
import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.DomSerializer
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants

object ImdbInfo {

  def getInfo(imdbId: String): (Option[BigDecimal], Boolean) = {
    val url = new URL(s"http://www.imdb.com/title/${imdbId}")
    val tagNode = new HtmlCleaner().clean(url)
    val doc: org.w3c.dom.Document = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val xpath = XPathFactory.newInstance().newXPath()
    val rawScore = xpath.evaluate("//span[contains(@class, 'rating')]", doc, XPathConstants.STRING).toString
    val rawIsTheatricalFilm = xpath.evaluate("//a[@title='See more release dates']", doc, XPathConstants.STRING).toString
    val score = if (rawScore.endsWith("/10")) Some(BigDecimal(rawScore.replace("/10", "")).setScale(1)) else None
    val isTheatricalFilm = List("TV Movie", "Video").exists { rawIsTheatricalFilm contains }
    if (score.isDefined) (score, isTheatricalFilm) else getInfoFromOldSite(imdbId)
  }

  def getInfoFromOldSite(imdbId: String): (Option[BigDecimal], Boolean) = {
    val url = s"http://www.imdb.com/title/${imdbId}"
    val cleaner = new HtmlCleaner
    val props = cleaner.getProperties
    val rootNode = cleaner.clean(new URL(url))
    val elementsTitle = rootNode.getElementsByName("title", true)
    val isTheatricalFilm = List("TV Movie", "Video").exists { elementsTitle(0).getText().toString.contains }
    val elements = rootNode.getElementsByName("div", true)
    for (elem ← elements) {
      val classType = elem.getAttributeByName("span")
      if (classType != null && classType.equalsIgnoreCase("titlePageSprite star-box-giga-star")) {
        // stories might be "dirty" with text like "'", clean it up
        val score = StringEscapeUtils.unescapeHtml4(elem.getText.toString)
        return (Some(BigDecimal(score.trim)), isTheatricalFilm)
      }
    }
    return (None, isTheatricalFilm)
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