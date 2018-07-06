
package au.csiro.data61.magda.search.elasticsearch

import cats.Show
import com.sksamuel.elastic4s.ElasticApi
import com.sksamuel.elastic4s.http.bulk.BulkImplicits
import com.sksamuel.elastic4s.http.cat.CatImplicits
import com.sksamuel.elastic4s.http.cluster.ClusterImplicits
import com.sksamuel.elastic4s.http.get.GetImplicits
import com.sksamuel.elastic4s.http.index._
import com.sksamuel.elastic4s.http.index.admin.IndexAdminImplicits
import com.sksamuel.elastic4s.http.locks.LocksImplicits
import com.sksamuel.elastic4s.http.nodes.NodesImplicits
import com.sksamuel.elastic4s.http.search.template.SearchTemplateImplicits
import com.sksamuel.elastic4s.http.search.{SearchScrollImplicits}
//--- In order to override the builder logic
import au.csiro.data61.magda.search.elasticsearch.Executables.{
  MappingExecutables,
  SearchImplicits,
  CountImplicits,
  DeleteImplicits,
  ExplainImplicits,
  IndexAliasImplicits,
  IndexTemplateImplicits,
  ReindexImplicits,
  UpdateImplicits,
  ValidateImplicits
}
import com.sksamuel.elastic4s.http.settings.SettingsImplicits
import com.sksamuel.elastic4s.http.snapshots.SnapshotHttpImplicits
import com.sksamuel.elastic4s.http.task.TaskImplicits
import com.sksamuel.elastic4s.http.termvectors.TermVectorsExecutables
import com.sksamuel.exts.Logging

trait ElasticDsl
  extends ElasticApi
    with Logging
    with BulkImplicits
    with CatImplicits
    with CountImplicits
    with ClusterImplicits
    with DeleteImplicits
    with ExistsImplicits
    with ExplainImplicits
    with GetImplicits
    with IndexImplicits
    with IndexAdminImplicits
    with IndexAliasImplicits
    with IndexStatsImplicits
    with IndexTemplateImplicits
    with LocksImplicits
    with MappingExecutables
    with NodesImplicits
    with ReindexImplicits
    with RolloverImplicits
    with SearchImplicits
    with SearchTemplateImplicits
    with SearchScrollImplicits
    with SettingsImplicits
    with SnapshotHttpImplicits
    with UpdateImplicits
    with TaskImplicits
    with TermVectorsExecutables
    with ValidateImplicits {
  implicit class RichRequest[T](req: T) {
    def show(implicit show: Show[T]): String = show.show(req)
  }
}

object ElasticDsl extends ElasticDsl
