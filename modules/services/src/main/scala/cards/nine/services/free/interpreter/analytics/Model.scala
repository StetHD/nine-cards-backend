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

package cards.nine.services.free.interpreter.analytics

import cards.nine.domain.analytics.{CountryIsoCode, DateRange}
import enumeratum._

object model {

  /*  https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#request-body */
  case class RequestBody(reportRequests: List[ReportRequest])

  object RequestBody {
    def apply(reportRequest: ReportRequest): RequestBody = RequestBody(List(reportRequest))
  }

  val maxPageSize: Int = 10000

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#reportrequest */
  case class ReportRequest(
      viewId: String,
      dimensions: List[Dimension],
      metrics: List[Metric],
      dateRanges: List[DateRange],
      dimensionFilterClauses: DimensionFilter.Clauses = Nil,
      orderBys: order.OrderBys = Nil,
      includeEmptyRows: Boolean = true,
      pageSize: Int = maxPageSize,
      hideTotals: Boolean = true,
      hideValueRanges: Boolean = true
  )

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#dimension */
  case class Dimension(name: String)
  object Dimension {
    val country        = apply("ga:country")
    val countryIsoCode = apply("ga:countryIsoCode")
    val continent      = apply("ga:continent")
    val category       = apply("ga:eventCategory")
    val packageName    = apply("ga:eventLabel")
  }

  /*  https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#metric */
  case class Metric(
      expression: String,
      alias: String,
      formattingType: MetricType
  )
  object Metric {
    val eventValue = new Metric(
      expression = "ga:eventValue",
      alias = "times_used",
      formattingType = MetricType.INTEGER
    )
  }

  object DimensionFilter {

    /* List of dimension filter clauses, combined with an AND */
    type Clauses = List[Clause]

    def singleClause(filter: Filter): Clauses =
      List(Clause(LogicalOperator.OPERATOR_UNSPECIFIED, List(filter)))

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#DimensionFilterClause */
    case class Clause(
        operator: LogicalOperator,
        filters: List[Filter]
    )

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#operator */
    sealed trait Operator extends EnumEntry
    object Operator extends Enum[Operator] {

      case object OPERATOR_UNSPECIFIED extends Operator
      case object REGEXP               extends Operator
      case object BEGINS_WITH          extends Operator
      case object ENDS_WITH            extends Operator
      case object PARTIAL              extends Operator
      case object EXACT                extends Operator
      case object IN_LIST              extends Operator

      val values = super.findValues
    }

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#FilterLogicalOperator */
    sealed trait LogicalOperator extends EnumEntry
    object LogicalOperator extends Enum[LogicalOperator] {
      case object OPERATOR_UNSPECIFIED extends LogicalOperator
      case object AND                  extends LogicalOperator
      case object OR                   extends LogicalOperator

      val values = super.findValues
    }

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#dimensionfilter */
    case class Filter(
        dimensionName: String,
        not: Boolean,
        operator: Operator,
        expressions: List[String],
        caseSensitive: Boolean
    )
    object Filter {

      def isCategory(category: String): Filter = Filter(
        dimensionName = Dimension.category.name,
        not = false,
        operator = Operator.EXACT,
        expressions = List(category),
        caseSensitive = false
      )

      def isCountry(code: CountryIsoCode): Filter = Filter(
        dimensionName = Dimension.countryIsoCode.name,
        not = false,
        operator = Operator.EXACT,
        expressions = List(code.value.toUpperCase),
        caseSensitive = false
      )
    }

  }

  object order {

    /* List of OrderBys: lexicographycally*/
    type OrderBys = List[order.OrderBy]

    object OrderBy {

      def alpha(dimension: Dimension) = new OrderBy(
        fieldName = dimension.name,
        orderType = OrderType.VALUE,
        sortOrder = SortOrder.ASCENDING
      )

      def greatest(metric: Metric) = new OrderBy(
        fieldName = metric.expression,
        orderType = OrderType.VALUE,
        sortOrder = SortOrder.DESCENDING
      )

      val category = alpha(Dimension.category)

      val country = alpha(Dimension.country)

      val countryIsoCode = alpha(Dimension.countryIsoCode)

      val continent = alpha(Dimension.continent)

      val eventValue = greatest(Metric.eventValue)
    }

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#orderby */
    case class OrderBy(
        fieldName: String,
        orderType: OrderType,
        sortOrder: SortOrder
    )

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#ordertype */
    sealed trait OrderType extends EnumEntry
    object OrderType extends Enum[OrderType] {
      case object VALUE extends OrderType

      val values = super.findValues
    }

    /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#ordertype */
    sealed trait SortOrder extends EnumEntry
    object SortOrder extends Enum[SortOrder] {
      case object SORT_ORDER_UNSPECIFIED extends SortOrder
      case object ASCENDING              extends SortOrder
      case object DESCENDING             extends SortOrder

      val values = super.findValues
    }

  }

  /* Responses */

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#response-body */
  case class ResponseBody(reports: List[Report]) extends AnyVal

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#Report */
  case class Report(
      columnHeader: ColumnHeader,
      data: ReportData
  )

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#columnheader*/
  case class ColumnHeader(
      dimensions: List[String],
      metricHeader: MetricHeader
  )

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#metricheader*/
  case class MetricHeader(metricHeaderEntries: List[MetricHeaderEntry])

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#MetricHeaderEntry*/
  case class MetricHeaderEntry(
      name: String,
      `type`: MetricType
  )
  object MetricHeaderEntry {
    val eventValue = apply(
      name = Metric.eventValue.alias,
      `type` = MetricType.INTEGER
    )
  }

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#reportdata */
  case class ReportData(
      rows: Option[List[ReportRow]],
      rowCount: Option[Int],
      isDataGolden: Option[Boolean]
  )

  /* A ReportRow is a "cell", that contains the values of metrics for one point in the dimension-space.
   * https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#reportrow */
  case class ReportRow(
      dimensions: List[String],
      metrics: List[DateRangeValues]
  )

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#daterangevalues */
  case class DateRangeValues(values: List[String])

  /* https://developers.google.com/analytics/devguides/reporting/core/v4/rest/v4/reports/batchGet#metrictype */
  sealed trait MetricType extends EnumEntry
  object MetricType extends Enum[MetricType] {

    case object METRIC_TYPE_UNSPECIFIED extends MetricType
    case object INTEGER                 extends MetricType
    case object FLOAT                   extends MetricType
    case object CURRENCY                extends MetricType
    case object PERCENT                 extends MetricType
    case object TIME                    extends MetricType

    val values = super.findValues
  }

}
