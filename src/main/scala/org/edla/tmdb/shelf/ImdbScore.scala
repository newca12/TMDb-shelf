package org.edla.tmdb.shelf

import org.htmlcleaner.HtmlCleaner
import java.net.URL
import org.apache.commons.lang3.StringEscapeUtils

object ImdbScore {

  def getScoreFromUrl(url: String): String = {
    val cleaner = new HtmlCleaner
    val props = cleaner.getProperties
    val rootNode = cleaner.clean(new URL(url))
    val elements = rootNode.getElementsByName("div", true)
    val score =
      for (elem ← elements) {
        val classType = elem.getAttributeByName("class")
        if (classType != null && classType.equalsIgnoreCase("titlePageSprite star-box-giga-star")) {
          // stories might be "dirty" with text like "'", clean it up
          val text = StringEscapeUtils.unescapeHtml4(elem.getText.toString)
          return text
        }
      }
    return "N/A"
  }

  def getScoreFromId(imdbId: String): Option[Float] = {
    val score = getScoreFromUrl(s"http://www.imdb.com/title/${imdbId}")
    score match {
      case "N/A" => None
      case s => Some(s.toFloat)
    }
  }

}