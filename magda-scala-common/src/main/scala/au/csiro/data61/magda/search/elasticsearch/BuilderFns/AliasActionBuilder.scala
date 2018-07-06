package au.csiro.data61.magda.search.elasticsearch.BuilderFns

import com.sksamuel.elastic4s.alias.{
  AddAliasActionDefinition,
  IndicesAliasesRequestDefinition,
  RemoveAliasActionDefinition
}
import com.sksamuel.elastic4s.json.{XContentBuilder, XContentFactory}

object AliasActionBuilder {

  def apply(r: IndicesAliasesRequestDefinition): XContentBuilder = {
    val source = XContentFactory.jsonBuilder().startArray("actions")

    val actionsArray = r.actions
      .map {
        case addAction: AddAliasActionDefinition       => buildAddAction(addAction).string()
        case removeAction: RemoveAliasActionDefinition => buildRemoveAction(removeAction).string()
      }
      .mkString(",")

    source.rawValue(actionsArray)

    source.endArray().endObject()
  }

  private def buildAddAction(addAction: AddAliasActionDefinition): XContentBuilder = {
    val jsonBuilder = XContentFactory.jsonBuilder().startObject("add")

    jsonBuilder.field("index", addAction.index)
    jsonBuilder.field("alias", addAction.alias)

    addAction.filter.map(QueryBuilderFn(_)).foreach { queryBuilder =>
      jsonBuilder.rawField("filter", queryBuilder)
    }
    addAction.routing.foreach(jsonBuilder.field("routing", _))
    addAction.searchRouting.foreach(jsonBuilder.field("search_routing", _))
    addAction.indexRouting.foreach(jsonBuilder.field("index_routing", _))

    jsonBuilder.endObject().endObject()
  }

  private def buildRemoveAction(removeAction: RemoveAliasActionDefinition): XContentBuilder = {
    val jsonBuilder = XContentFactory.jsonBuilder().startObject("remove")

    jsonBuilder.field("index", removeAction.index)
    jsonBuilder.field("alias", removeAction.alias)

    removeAction.filter.map(QueryBuilderFn(_)).foreach { queryBuilder =>
      jsonBuilder.rawField("filter", queryBuilder)
    }
    removeAction.routing.foreach(jsonBuilder.field("routing", _))
    removeAction.searchRouting.foreach(jsonBuilder.field("search_routing", _))
    removeAction.indexRouting.foreach(jsonBuilder.field("index_routing", _))

    jsonBuilder.endObject().endObject()
    jsonBuilder
  }
}
