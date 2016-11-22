package cards.nine.domain

import cards.nine.domain.account.Email
import cards.nine.domain.analytics._
import cards.nine.domain.application.{ Category, Moment, Package, PriceFilter }
import cards.nine.domain.oauth.ServiceAccount
import cards.nine.domain.pagination.Page
import com.fortysevendeg.scalacheck.datetime.instances.joda._
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import enumeratum.{ Enum, EnumEntry }
import org.joda.time.{ DateTime, Period }
import org.scalacheck.{ Arbitrary, Gen }

import sys.process._

object ScalaCheck {

  import Gen._

  def arbEnumeratum[C <: EnumEntry](e: Enum[C]): Arbitrary[C] = Arbitrary(Gen.oneOf(e.values))

  implicit val arbCategory: Arbitrary[Category] = arbEnumeratum[Category](Category)

  implicit val arbDistinctCategories: Arbitrary[List[Category]] = Arbitrary {
    for {
      size ← Gen.choose(10, 20)
      categories ← Gen.pick(size, Category.values)
    } yield categories.toList
  }

  implicit val arbGeoScope: Arbitrary[GeoScope] = Arbitrary(
    for {
      code ← Gen.listOfN(2, Gen.alphaChar)
      scope ← Gen.oneOf(WorldScope, CountryScope(CountryIsoCode(code.mkString)))
    } yield scope
  )

  implicit val arbMoment: Arbitrary[Moment] = arbEnumeratum[Moment](Moment)

  implicit val arbPackage: Arbitrary[Package] = Arbitrary {
    Gen.listOfN(16, Gen.alphaNumChar) map (l ⇒ Package(l.mkString))
  }

  implicit val arbPage: Arbitrary[Page] = Arbitrary {
    for {
      pageSize ← Gen.posNum[Long]
      pageNumber ← Gen.posNum[Long]
    } yield Page(pageNumber, pageSize)
  }

  implicit val arbPriceFilter: Arbitrary[PriceFilter] = arbEnumeratum[PriceFilter](PriceFilter)

  implicit val arbDateRange: Arbitrary[DateRange] = Arbitrary {
    val rangeGenerator = genDateTimeWithinRange(DateTime.now, Period.months(1))
    for {
      date1 ← rangeGenerator
      date2 ← rangeGenerator
    } yield {
      if (date1.isBefore(date2))
        DateRange(date1, date2)
      else
        DateRange(date2, date1)
    }
  }

  def nonEmptyString(maxSize: Int) =
    Gen.resize(
      s = maxSize,
      g = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
    )

  def fixedLengthString(size: Int) = Gen.listOfN(size, Gen.alphaChar).map(_.mkString)

  def fixedLengthNumericString(size: Int) = Gen.listOfN(size, Gen.numChar).map(_.mkString)

  val emailGenerator: Gen[Email] = for {
    mailbox ← nonEmptyString(50)
    topLevelDomain ← nonEmptyString(45)
    domain ← fixedLengthString(3)
  } yield Email(s"$mailbox@$topLevelDomain.$domain")

  implicit val abEmail: Arbitrary[Email] = Arbitrary(emailGenerator)

  /** Generates an hexadecimal character */
  def hexChar: Gen[Char] = Gen.frequency(
    (10, Gen.numChar),
    (6, choose(97.toChar, 102.toChar))
  )

  val genServiceAccount: Gen[ServiceAccount] = {
    // We get these formats from reading one example of credentials

    /*
     * Generating a PKCS8 Private Key in Base64 format is a lot of work, not worth it
     * http://stackoverflow.com/questions/5127379/how-to-generate-a-rsa-keypair-with-a-privatekey-encrypted-with-password/6164414#6164414
     */
    val privateKeyGen: Gen[String] = {
      def commandLinePkcs8(i: Int): String = {
        // This is an ugly hacks but seems to do the job
        val logger = ProcessLogger(_ ⇒ (), _ ⇒ ()) // ignore all outputs
        ("openssl genrsa" #| "openssl pkcs8 -topk8 -nocrypt").!!(logger)
      }
      Gen.const(1).map(commandLinePkcs8)
    }

    val privateKeyIdGen: Gen[String] = Gen.listOfN(41, hexChar).map(_.mkString)

    val clientIdGen: Gen[String] = Gen.listOfN(22, Gen.numChar).map(_.mkString)

    for {
      clientId ← clientIdGen
      clientEmail ← emailGenerator.map(_.value)
      privateKey ← privateKeyGen
      privateKeyId ← privateKeyIdGen
      tokenUri ← Gen.alphaStr
      scopes ← Gen.listOf(Gen.alphaStr)
    } yield ServiceAccount(
      clientId     = clientId,
      clientEmail  = clientEmail,
      privateKey   = privateKey,
      privateKeyId = privateKeyId,
      tokenUri     = tokenUri,
      scopes       = scopes
    )

  }

  implicit val arbServiceAccount: Arbitrary[ServiceAccount] = Arbitrary(genServiceAccount)

}

