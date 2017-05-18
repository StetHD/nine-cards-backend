/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cards.nine.commons

import cards.nine.commons.config.NineCardsConfig
import org.specs2.mutable.Specification

class NineCardsConfigSpec extends Specification {

  val intValue     = 123
  val stringValue  = "abc"
  val stringValue2 = "xyz"
  val booleanValue = true

  val mapValue = Map(
    "karl" → "microsoft",
    "marx" → "made",
    "gave" → "no"
  )

  val dummyConfigHocon =
    s"""
       |nineCards {
       |  intValue = $intValue
       |  stringValue = $stringValue
       |  booleanValue = $booleanValue
       |  emptyStringListValue = []
       |  singletonStringListValue = [$stringValue]
       |  manyStringListValue = [$stringValue, $stringValue2 ]
       |  mapValue {
       |    karl = "microsoft"
       |    marx = "made"
       |    gave = "no"
       |  }
       |}
     """.stripMargin

  val config = new NineCardsConfig(Option(dummyConfigHocon))

  private[this] val ifExists    = "if the key exists in the config file"
  private[this] val ifNotExists = "if the key doesn't exist in the config file"

  "getBoolean" should {
    s"return an Boolean value $ifExists" in {
      config.getBoolean("nineCards.booleanValue") must_== booleanValue
    }
  }

  "getInt" should {
    s"return an Int value $ifExists" in {
      config.getInt("nineCards.intValue") must_== intValue
    }
  }

  "getString" should {
    s"return a String value $ifExists" in {
      config.getString("nineCards.stringValue") must_== stringValue
    }
  }

  "getStringList" should {

    s"return an empty list of String values $ifExists and the value is an empty list" in {
      config.getStringList("nineCards.emptyStringListValue") must_== List()
    }

    s"return a non-empty list of String values $ifExists and the value is not empty" in {
      config.getStringList("nineCards.singletonStringListValue") must_== List(stringValue)
    }

    s"return a list of several values $ifExists and the value is a comma-separated list" in {
      config.getStringList("nineCards.manyStringListValue") must_== List(stringValue, stringValue2)
    }

  }

  "getBoolean" should {
    s"return some Boolean value $ifExists" in {
      config.getOptionalBoolean("nineCards.booleanValue") must beSome[Boolean](booleanValue)
    }

    s"return None $ifNotExists" in {
      config.getOptionalBoolean("booleanValue") must beNone
    }
  }

  "getOptionalInt" should {
    s"return some Int value $ifExists" in {
      config.getOptionalInt("nineCards.intValue") must beSome[Int](intValue)
    }

    s"return None $ifNotExists " in {
      config.getOptionalInt("intValue") must beNone
    }
  }

  "getOptionalString" should {
    s"return some String value $ifExists" in {
      config.getOptionalString("nineCards.stringValue") must beSome[String](stringValue)
    }

    s"return None $ifNotExists" in {
      config.getOptionalString("stringValue") must beNone
    }
  }

  "getMap" should {
    s"return the map of String $ifExists" in {
      val actual = config.getMap("nineCards.mapValue").toList
      actual must containTheSameElementsAs(mapValue.toList)
    }
  }

}
