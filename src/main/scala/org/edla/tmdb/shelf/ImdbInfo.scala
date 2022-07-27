package org.edla.tmdb.shelf

import org.htmlcleaner.{CleanerProperties, DomSerializer, HtmlCleaner, TagNode}

import java.net.URL
import javax.xml.xpath.{XPath, XPathConstants, XPathFactory}

object ImdbInfo {

  def getInfo(imdbId: String): (Option[BigDecimal], Option[Boolean]) = {
    val url              = new URL(s"https://www.imdb.com/title/$imdbId/")
    val cleaner          = new HtmlCleaner()
    val tagNode: TagNode = cleaner.clean(url)
    //println("<" + tagNode.getName + ">" + cleaner.getInnerHtml(tagNode) + "</" + tagNode.getName + ">")
    val doc: org.w3c.dom.Document = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val xpath: XPath              = XPathFactory.newInstance().newXPath()
    val (rawScore, rawIsNotTheatricalFilm) = {
      newSite(xpath, doc) match {
        case (a, b) if (a.isEmpty && b.isEmpty) => oldSite(xpath, doc)
        case (a, b)                             => (a, b)
      }
    }

    val score =
      if (rawScore.isEmpty) {
        None
      } else {
        Some(BigDecimal(rawScore.split("/").head))
      }
    val isNotTheatricalFilm = Some(
      List("TV Movie", "TV Short", "Video", "Episode aired", "TV Series", "TV Special", "Téléfilm").exists {
        rawIsNotTheatricalFilm.contains
      }
    )
    (score, isNotTheatricalFilm)
  }

  def oldSite(xpath: XPath, doc: org.w3c.dom.Document): (String, String) = {
    (
      xpath
        .evaluate(
          "//span[@class='sc-7ab21ed2-1 jGRxWM']",
          doc,
          XPathConstants.STRING
        )
        .toString,
      xpath
        .evaluate("//li[@class='ipc-inline-list__item']", doc, XPathConstants.STRING)
        .toString
    )
  }

  def newSite(xpath: XPath, doc: org.w3c.dom.Document): (String, String) = {
    (
      xpath
        .evaluate(
          "//span[@class='sc-7ab21ed2-1 jGRxWM']",
          doc,
          XPathConstants.STRING
        )
        .toString,
      xpath
        .evaluate(
          "//ul[@class='ipc-inline-list ipc-inline-list--show-dividers sc-8c396aa2-0 kqWovI baseAlt']",
          doc,
          XPathConstants.STRING
        )
        .toString
    )
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
