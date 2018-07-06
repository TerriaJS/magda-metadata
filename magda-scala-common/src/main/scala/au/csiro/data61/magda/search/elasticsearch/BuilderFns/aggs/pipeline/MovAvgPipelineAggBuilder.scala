package au.csiro.data61.magda.search.elasticsearch.BuilderFns.aggs.pipeline

import au.csiro.data61.magda.search.elasticsearch.BuilderFns.aggs.AggMetaDataFn
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}
import com.sksamuel.elastic4s.searches.aggs.pipeline.MovAvgDefinition

object MovAvgPipelineAggBuilder {
  def apply(agg: MovAvgDefinition): XContentBuilder = {
    val builder = XContentFactory.jsonBuilder()
    builder.startObject("moving_avg")
    builder.field("buckets_path", agg.bucketsPath)
    agg.gapPolicy.foreach(policy => builder.field("gap_policy", policy.toString.toLowerCase))
    agg.format.foreach(f => builder.field("format", f))
    agg.minimize.foreach(m => builder.field("minimize", m))
    agg.numPredictions.foreach(p => builder.field("predict", p))

    if (agg.settings.nonEmpty) {
      builder.startObject("settings")
      agg.settings.foreach { case (k, v) => builder.autofield(k, v) }
      builder.endObject()
    }
    agg.window.foreach(w => builder.field("window", w))
    builder.endObject()
    AggMetaDataFn(agg, builder)
    builder.endObject()
  }
}
