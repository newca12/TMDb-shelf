package org.edla.tmdb.shelf

import org.scalatest.PropSpec
import org.scalatest.Matchers

class ImdbInfoTest extends PropSpec with Matchers {
  property("Get correct ratings and detect theatrical films") {
    ImdbInfo.getInfo("tt1390411") shouldBe ((Some(6.9), Some(false)))
    ImdbInfo.getInfo("tt0304584") shouldBe ((Some(4.4), Some(true)))
    ImdbInfo.getInfo("tt0827521") shouldBe ((Some(5.6), Some(true)))
    ImdbInfo.getInfo("tt0001539") shouldBe ((None, Some(false)))
    ImdbInfo.getInfo("tt5031232") shouldBe ((Some(8.8), Some(true)))
    ImdbInfo.getInfo("tt4049416") shouldBe ((Some(6.3), Some(true)))
  }
}
