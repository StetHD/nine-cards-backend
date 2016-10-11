package cards.nine.googleplay.processes

import cats.data.Xor
import cards.nine.googleplay.domain.{ Package, FullCard, GoogleAuthParams }

package object getcard {
  sealed trait FailedResponse { val packageName: Package }
  case class WrongAuthParams(packageName: Package, authParams: GoogleAuthParams) extends FailedResponse
  case class PendingResolution(packageName: Package) extends FailedResponse
  case class UnknownPackage(packageName: Package) extends FailedResponse
  type Response = FailedResponse Xor FullCard
}

object ResolveMany {
  case class Response(notFound: List[Package], pending: List[Package], apps: List[FullCard])
}

object ResolvePending {

  case class Response(solved: List[Package], unknown: List[Package], pending: List[Package])

  sealed trait PackageStatus
  case object Resolved extends PackageStatus
  case object Pending extends PackageStatus
  case object Unknown extends PackageStatus
}

