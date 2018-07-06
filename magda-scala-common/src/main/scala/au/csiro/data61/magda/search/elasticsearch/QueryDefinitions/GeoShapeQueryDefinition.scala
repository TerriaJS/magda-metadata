
package au.csiro.data61.magda.search.elasticsearch.QueryDefinitions

import com.sksamuel.elastic4s.Index
import com.sksamuel.elastic4s.searches.GeoPoint
import com.sksamuel.elastic4s.searches.queries.QueryDefinition
import com.sksamuel.elastic4s.searches.queries.geo.Shapes.{Circle, Polygon}
import com.sksamuel.exts.OptionImplicits._
import com.sksamuel.elastic4s.json.XContentBuilder

sealed trait ShapeDefinition {
  def geoShapeType: GeoShapeType
}

sealed trait SingleShape extends ShapeDefinition

case class PointShape(point: GeoPoint) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.POINT
}

case class CircleShape(circle: Circle) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.CIRCLE
}

case class PolygonShape(polygon: Polygon) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.POLYGON
}

case class MultiPointShape(points: Seq[GeoPoint]) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.MULTIPOINT
}

case class LineStringShape(
                            p1: GeoPoint,
                            p2: GeoPoint,
                            path: GeoPoint*
                          ) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.LINESTRING
}

case class EnvelopeShape(
                          upperLeft: GeoPoint,
                          lowerRight: GeoPoint
                        ) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.ENVELOPE
}

case class MultiLineStringShape(coordinates: Seq[Seq[GeoPoint]]) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.MULTILINESTRING
}

case class MultiPolygonShape(coordinate: Seq[Polygon]) extends SingleShape {
  def geoShapeType: GeoShapeType = GeoShapeType.MULTIPOLYGON
}

sealed trait CollectionShape extends ShapeDefinition
case class GeometryCollectionShape(shapes: Seq[SingleShape]) extends CollectionShape {
  def geoShapeType: GeoShapeType = GeoShapeType.GEOMETRYCOLLECTION
}

sealed trait Shape
case class InlineShape(shape: ShapeDefinition)                                     extends Shape
case class PreindexedShape(id: String, index: Index, `type`: String, path: String) extends Shape

case class GeoShapeQueryDefinition(field: String,
                                   shape: Shape,
                                   relation: Option[ShapeRelation] = None,
                                   boost: Option[Double] = None,
                                   queryName: Option[String] = None,
                                   strategy: Option[SpatialStrategy] = None,
                                   ignoreUnmapped: Option[Boolean] = None)
  extends QueryDefinition {

  def relation(relation: ShapeRelation): GeoShapeQueryDefinition   = copy(relation = relation.some)
  def boost(boost: Double): GeoShapeQueryDefinition                = copy(boost = boost.some)
  def queryName(queryName: String): GeoShapeQueryDefinition        = copy(queryName = queryName.some)
  def strategy(strategy: SpatialStrategy): GeoShapeQueryDefinition = copy(strategy = strategy.some)

  def inlineShape(shape: ShapeDefinition) = copy(shape = InlineShape(shape))
  def preindexedShape(id: String, index: Index, `type`: String, path: String) =
    copy(shape = PreindexedShape(id, index, `type`, path))

  def ignoreUnmapped(ignore: Boolean): GeoShapeQueryDefinition = copy(ignoreUnmapped = ignore.some)

  def build(source: XContentBuilder): Unit = {
    if(relation.isDefined) relation.foreach {
      case ShapeRelation.INTERSECTS => source.field("relation", "INTERSECTS")
      case ShapeRelation.DISJOINT => source.field("relation", "DISJOINT")
      case ShapeRelation.WITHIN => source.field("relation", "WITHIN")
      case ShapeRelation.CONTAINS => source.field("relation", "CONTAINS")
    }
  }
}



trait GeoShapeType
object GeoShapeType {
  case object POINT              extends GeoShapeType
  case object MULTIPOINT         extends GeoShapeType
  case object LINESTRING         extends GeoShapeType
  case object MULTILINESTRING    extends GeoShapeType
  case object POLYGON            extends GeoShapeType
  case object MULTIPOLYGON       extends GeoShapeType
  case object GEOMETRYCOLLECTION extends GeoShapeType
  case object ENVELOPE           extends GeoShapeType
  case object CIRCLE             extends GeoShapeType
}

trait ShapeRelation
object ShapeRelation {
  case object INTERSECTS extends ShapeRelation
  case object DISJOINT   extends ShapeRelation
  case object WITHIN     extends ShapeRelation
  case object CONTAINS   extends ShapeRelation
  val intersects = INTERSECTS
  val disjoint   = DISJOINT
  val within     = WITHIN
  val contains   = CONTAINS
}

trait SpatialStrategy
object SpatialStrategy {
  case object Term      extends SpatialStrategy
  case object Recursive extends SpatialStrategy
}
