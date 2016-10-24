package cards.nine.services.persistence

import java.sql.Timestamp
import java.time.Instant

import cards.nine.domain.account._
import cards.nine.domain.analytics._
import cards.nine.domain.application.{ Moment, Package }
import cards.nine.services.free.domain.Ranking.GoogleAnalyticsRanking
import cards.nine.domain.ScalaCheck._
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cards.nine.services.free.interpreter.user.Services.UserData
import cards.nine.services.persistence.NineCardsGenEntities._
import cats.instances.list._
import cats.instances.map._
import cats.syntax.semigroup._
import org.scalacheck.{ Arbitrary, Gen }

object NineCardsGenEntities {

  case class PublicIdentifier(value: String) extends AnyVal

  case class WrongIsoCode2(value: String) extends AnyVal

  case class CollectionTitle(value: String) extends AnyVal
}

trait NineCardsScalacheckGen {

  val timestampGenerator: Gen[Timestamp] = Gen.choose(0l, 253402300799l) map { seconds ⇒
    Timestamp.from(Instant.ofEpochSecond(seconds))
  }

  val sharedCollectionDataGenerator: Gen[SharedCollectionData] = for {
    publicIdentifier ← Gen.uuid
    publishedOn ← timestampGenerator
    author ← Gen.alphaStr
    name ← Gen.alphaStr
    views ← Gen.posNum[Int]
    category ← nonEmptyString(64)
    icon ← nonEmptyString(64)
    community ← Gen.oneOf(true, false)
    packages ← Gen.listOf(arbPackage.arbitrary)
  } yield SharedCollectionData(
    publicIdentifier = publicIdentifier.toString,
    userId           = None,
    publishedOn      = publishedOn,
    author           = author,
    name             = name,
    views            = views,
    category         = category,
    icon             = icon,
    community        = community,
    packages         = packages map (_.value)
  )

  val userDataGenerator: Gen[UserData] = for {
    email ← emailGenerator
    apiKey ← Gen.uuid
    sessionToken ← Gen.uuid
  } yield UserData(email.value, apiKey.toString, sessionToken.toString)

  implicit val abAndroidId: Arbitrary[AndroidId] = Arbitrary(Gen.uuid.map(u ⇒ AndroidId(u.toString)))

  implicit val abApiKey: Arbitrary[ApiKey] = Arbitrary(Gen.uuid.map(u ⇒ ApiKey(u.toString)))

  implicit val abCollectionTitle: Arbitrary[CollectionTitle] = Arbitrary(Gen.alphaStr.map(CollectionTitle))

  implicit val abDeviceToken: Arbitrary[DeviceToken] = Arbitrary(Gen.uuid.map(u ⇒ DeviceToken(u.toString)))

  implicit val abPublicIdentifier: Arbitrary[PublicIdentifier] = Arbitrary(Gen.uuid.map(u ⇒ PublicIdentifier(u.toString)))

  implicit val abSessionToken: Arbitrary[SessionToken] = Arbitrary(Gen.uuid.map(u ⇒ SessionToken(u.toString)))

  implicit val abSharedCollectionData: Arbitrary[SharedCollectionData] = Arbitrary(sharedCollectionDataGenerator)

  implicit val abUserData: Arbitrary[UserData] = Arbitrary(userDataGenerator)

  implicit val abWrongIsoCode2: Arbitrary[WrongIsoCode2] = Arbitrary(fixedLengthNumericString(2).map(WrongIsoCode2.apply))

  val genRankingByCategory: Gen[(String, List[Package])] =
    for {
      category ← arbCategory.arbitrary
      size ← Gen.choose(10, 20)
      packages ← Gen.listOfN(size, arbPackage.arbitrary)
    } yield (category.entryName, packages)

  val genRankingsByCategory =
    for {
      categories ← arbDistinctCategories.arbitrary
      categoriesCount = categories.size
      packagesCount ← Gen.choose(10, 20)
      packages ← Gen.listOfN(categoriesCount, Gen.listOfN(packagesCount, arbPackage.arbitrary))
    } yield {
      categories
        .map(_.entryName)
        .zip(packages)
    }

  implicit val arbRanking: Arbitrary[GoogleAnalyticsRanking] = Arbitrary {
    for {
      size ← Gen.choose(10, 20)
      categoryRanking ← Gen.listOfN(size, genRankingByCategory)
    } yield GoogleAnalyticsRanking(categoryRanking.toMap)
  }

  implicit val arbUnrankedAppList: Arbitrary[Set[UnrankedApp]] = Arbitrary {
    for {
      size ← Gen.choose(10, 20)
      categoryRanking ← Gen.listOfN(size, genRankingByCategory)
    } yield categoryRanking
      .flatMap { case (category, packages) ⇒ packages map (p ⇒ UnrankedApp(p, category)) }
      .toSet
  }

  private[this] def toPackageList(data: List[(String, List[Package])]) =
    data flatMap { case (category, packages) ⇒ packages }

  private[this] def toUnrankedAppList(data: List[(String, List[Package])]) =
    data flatMap { case (category, packages) ⇒ packages map (UnrankedApp(_, category)) }

  private[this] def mergeCategoryAndMoments(data: List[(String, List[Package])], moments: List[String]) =
    data
      .flatMap {
        case (category, packages) ⇒
          (category +: moments) map (c ⇒ (c, packages))
      }

  private[this] def splitRankingsByCategory(data: List[(String, List[Package])], groupsCount: Int) =
    data.grouped((data.size + groupsCount - 1) / groupsCount).toList

  case class GetRankingForAppsSample(
    unrankedApps: Set[UnrankedApp],
    appsWithRanking: List[Package],
    appsWithoutRanking: List[Package],
    ranking: GoogleAnalyticsRanking
  )

  implicit val arbGetRankingForAppsSample: Arbitrary[GetRankingForAppsSample] = Arbitrary {
    for {
      rankingsByCategory ← genRankingsByCategory
      moments ← Gen.someOf(Moment.values.map(_.entryName))
      List(unrankedCategories, onlyCategories, categoriesAndMoments) = splitRankingsByCategory(rankingsByCategory, 3)
      unrankedAppsSample1 ← Gen.someOf(onlyCategories)
      unrankedAppsSample2 ← Gen.someOf(categoriesAndMoments)
    } yield {
      GetRankingForAppsSample(
        unrankedApps       = (toUnrankedAppList(unrankedCategories) ++
          toUnrankedAppList(unrankedAppsSample1.toList) ++
          toUnrankedAppList(unrankedAppsSample2.toList)).toSet,
        appsWithRanking    = toPackageList(unrankedAppsSample1.toList ++ unrankedAppsSample2.toList),
        appsWithoutRanking = toPackageList(unrankedCategories),
        ranking            = GoogleAnalyticsRanking(
          onlyCategories.toMap combine mergeCategoryAndMoments(categoriesAndMoments, moments.toList).toMap
        )
      )
    }
  }

}
