package au.csiro.data61.magda.search.elasticsearch.BuilderFns

import com.sksamuel.elastic4s.count.CountDefinition
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}

object CountBodyBuilderFn {
  def apply(request: CountDefinition): XContentBuilder = {
    val builder = XContentFactory.jsonBuilder()
    request.query.map(QueryBuilderFn.apply).foreach(builder.rawField("query", _))
    builder
  }
}
