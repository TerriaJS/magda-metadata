package au.csiro.data61.magda.search.elasticsearch.BuilderFns.aggs

import com.sksamuel.elastic4s.http.ScriptBuilderFn
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.searches.aggs.SumAggregationDefinition

object SumAggregationBuilder {
  def apply(agg: SumAggregationDefinition): XContentBuilder = {
    val builder = XContentFactory.jsonBuilder().startObject("sum")
    agg.field.foreach(builder.field("field", _))
    agg.missing.foreach(builder.autofield("missing", _))
    agg.script.foreach { script =>
      builder.rawField("script", ScriptBuilderFn(script))
    }
    builder
  }
}
