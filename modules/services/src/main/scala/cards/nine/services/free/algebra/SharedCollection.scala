package cards.nine.services.free.algebra

import cards.nine.domain.application.Package
import cards.nine.services.free.domain
import cards.nine.services.free.interpreter.collection.Services.SharedCollectionData
import cats.free.Free
import io.freestyle.free

object SharedCollection {

  @free trait Services[F[_]] {

    def add(collection: SharedCollectionData): Free[F, domain.SharedCollection]

    def addPackages(collection: Long, packages: List[Package]): Free[F, Int]

    def getById(id: Long): Free[F, Option[domain.SharedCollection]]

    def getByPublicId(publicId: String): Free[F, Option[domain.SharedCollection]]

    def getByUser(user: Long): Free[F, List[domain.SharedCollectionWithAggregatedInfo]]

    def getLatestByCategory(category: String, pageNumber: Int, pageSize: Int): Free[F, List[domain.SharedCollection]]

    def getTopByCategory(category: String, pageNumber: Int, pageSize: Int): Free[F, List[domain.SharedCollection]]

    def getPackagesByCollection(collection: Long): Free[F, List[domain.SharedCollectionPackage]]

    def update(id: Long, title: String): Free[F, Int]

    def updatePackages(collection: Long, packages: List[Package]): Free[F, (List[Package], List[Package])]
  }

}
