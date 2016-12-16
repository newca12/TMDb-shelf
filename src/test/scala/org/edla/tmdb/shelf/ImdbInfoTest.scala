package org.edla.tmdb.shelf

import org.scalatest.PropSpec
import org.scalatest.Matchers

class ImdbInfoTest extends PropSpec with Matchers {
  property("Get correct ratings and detect theatrical films") {
    ImdbInfo.getInfo("tt1390411") shouldBe ((Some(6.9), Some(false)))
    ImdbInfo.getInfo("tt0304584") shouldBe ((Some(4.3), Some(true)))
    ImdbInfo.getInfo("tt0827521") shouldBe ((Some(5.7), Some(true)))
    ImdbInfo.getInfo("tt0001539") shouldBe ((None, Some(false)))
  }
}