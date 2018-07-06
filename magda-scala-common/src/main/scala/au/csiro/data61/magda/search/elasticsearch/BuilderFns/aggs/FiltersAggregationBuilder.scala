package au.csiro.data61.magda.search.elasticsearch.BuilderFns.aggs

import au.csiro.data61.magda.search.elasticsearch.BuilderFns.QueryBuilderFn
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.searches.aggs.FiltersAggregationDefinition

object FiltersAggregationBuilder {
  def apply(agg: FiltersAggregationDefinition): XContentBuilder = {

    val builder = XContentFactory.jsonBuilder()

    val filters = {
      builder.startArray("filters")
      val filters = agg.filters.map(QueryBuilderFn.apply).map(_.string).mkString(",")
      builder.rawValue(filters)
      builder.endArray()
    }

    builder.rawField("filters", filters)

    SubAggsBuilderFn(agg, builder)
    AggMetaDataFn(agg, builder)
    builder
  }
}
