import cards.nine.domain.application.{Category, Moments}
import cards.nine.domain.ScalaCheck._
import org.scalacheck.Gen

Gen.oneOf(Category.values.filter(c ⇒ Moments.isMoment(c.entryName))).sample