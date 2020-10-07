package au.csiro.data61.magda.registry

import java.sql.SQLException

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.scaladsl.Source
import au.csiro.data61.magda.model.Registry._
import au.csiro.data61.magda.model.TenantId._
import au.csiro.data61.magda.opa.OpaTypes._
import au.csiro.data61.magda.registry.SqlHelper.{
  aspectQueryToSql,
  getOpaConditions
}
import gnieh.diffson._
import gnieh.diffson.sprayJson._
import scalikejdbc._
import spray.json._
import spray.json.lenses.JsonLenses._
import org.everit.json.schema.ValidationException
import au.csiro.data61.magda.client.AuthOperations

import scala.util.{Failure, Success, Try}
import com.typesafe.config.Config
import scalikejdbc.interpolation.SQLSyntax

trait RecordPersistence {

  def getValidRecordIds(
      tenantId: TenantId,
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      recordIds: Seq[String]
  )(implicit session: DBSession): Seq[String]

  def getAll(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[String],
      start: Option[Int],
      limit: Option[Int]
  )(implicit session: DBSession): RecordsPage[RecordSummary]

  def getAllWithAspects(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      optionalAspectIds: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[Long] = None,
      start: Option[Int] = None,
      limit: Option[Int] = None,
      dereference: Option[Boolean] = None,
      aspectQueries: Iterable[AspectQuery] = Nil,
      aspectOrQueries: Iterable[AspectQuery] = Nil,
      orderBy: Option[OrderByDef] = None
  )(implicit session: DBSession): RecordsPage[Record]

  def getCount(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String],
      aspectQueries: Iterable[AspectQuery] = Nil,
      aspectOrQueries: Iterable[AspectQuery] = Nil
  )(implicit session: DBSession): Long

  def getReadiness(
      logger: Option[LoggingAdapter] = None
  )(implicit session: DBSession): Option[Boolean]

  def getById(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      id: String
  )(implicit session: DBSession): Option[RecordSummary]

  def getByIdWithAspects(
      tenantId: TenantId,
      id: String,
      recordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      linkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): Option[Record]

  def getByIdsWithAspects(
      tenantId: TenantId,
      ids: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): RecordsPage[Record]

  def getRecordsLinkingToRecordIds(
      tenantId: TenantId,
      ids: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      idsToExclude: Iterable[String] = Seq(),
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): RecordsPage[Record]

  def getRecordAspectById(
      tenantId: TenantId,
      recordId: String,
      aspectId: String,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]]
  )(implicit session: DBSession): Option[JsObject]

  def getPageTokens(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      limit: Option[Int] = None,
      recordSelector: Iterable[Option[SQLSyntax]] = Iterable()
  )(implicit session: DBSession): List[String]

  def putRecordById(
      tenantId: SpecifiedTenantId,
      id: String,
      newRecord: Record,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record]

  def processRecordPatchOperationsOnAspects[T](
      recordPatch: JsonPatch,
      onReplaceAspect: (String, JsObject) => T,
      onPatchAspect: (String, JsonPatch) => T,
      onDeleteAspect: String => T
  ): Iterable[T]

  def patchRecordById(
      tenantId: SpecifiedTenantId,
      id: String,
      recordPatch: JsonPatch,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record]

  def patchRecordAspectById(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      aspectPatch: JsonPatch,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject]

  def putRecordAspectById(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      newAspect: JsObject,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject]

  def createRecord(
      tenantId: SpecifiedTenantId,
      record: Record,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record]

  def deleteRecord(
      tenantId: SpecifiedTenantId,
      recordId: String,
      userId: String
  )(implicit session: DBSession): Try[Boolean]

  def trimRecordsBySource(
      tenantId: SpecifiedTenantId,
      sourceTagToPreserve: String,
      sourceId: String,
      userId: String,
      logger: Option[LoggingAdapter] = None
  )(implicit session: DBSession): Try[Long]

  def createRecordAspect(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      aspect: JsObject,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject]

  def deleteRecordAspect(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      userId: String
  )(implicit session: DBSession): Try[Boolean]

  def reconstructRecordFromEvents(
      id: String,
      events: Source[RegistryEvent, NotUsed],
      aspects: Iterable[String],
      optionalAspects: Iterable[String]
  ): Source[Option[Record], NotUsed]

  /**
    * Gets a list of all potential policy ids for a given operation and (optional) set of records.
    * Will return the config default policy id as part of the list if any record within the scope
    * has a policy id of NULL.
    */
  def getPolicyIds(
      operation: AuthOperations.OperationType,
      recordIds: Option[Set[String]] = None
  )(implicit session: DBSession): Try[List[String]]

  /** Given a record and aspects being requested, returns the ids of all records that the record/aspect id combinations link to */
  def getLinkedRecordIds(
      operation: AuthOperations.OperationType,
      recordId: String,
      aspectIds: Iterable[String] = List()
  )(implicit session: DBSession): Try[Iterable[String]]

  /**
    * Given a list of aspect ids, queries each of them to see if the aspects link to other aspects.
    *
    * Note that currently, links can only be at the first level of an aspect, and only one is supported
    * per aspect.
    *
    * @return a Map of aspect ids to the first field inside that aspect that links to another aspect.
    */
  def buildReferenceMap(
      aspectIds: Iterable[String]
  )(implicit session: DBSession): Map[String, PropertyWithLink]
}

class DefaultRecordPersistence(config: Config)
    extends Protocols
    with DiffsonProtocol
    with RecordPersistence {
  val aspectValidator = new AspectValidator(config, this)
  val maxResultCount = 1000
  val defaultResultCount = 100

  val defaultOpaPolicyId =
    if (config.hasPath("opa.recordPolicyId"))
      Some(config.getString("opa.recordPolicyId"))
    else None

  /**
    * Given a list of recordIds, filter out any record that the current user has not access and return the rest of ids
    *
    * @param tenantId
    * @param opaRecordQueries
    * @param recordIds
    * @param session
    * @return Seq[String]
    */
  def getValidRecordIds(
      tenantId: TenantId,
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      recordIds: Seq[String]
  )(implicit session: DBSession): Seq[String] =
    opaRecordQueries match {
      case None => recordIds
      case Some(_) =>
        this
          .getRecords(
            tenantId = tenantId,
            aspectIds = Seq(),
            optionalAspectIds = Seq(),
            recordOpaQueries = opaRecordQueries,
            linkedRecordOpaQueries = None,
            pageToken = None,
            start = None,
            Some(recordIds.size),
            dereference = Some(false),
            orderBy = None,
            maxLimit = Some(recordIds.size),
            recordSelector = Seq(
              Some(
                SQLSyntax.in(SQLSyntax.createUnsafely("recordId"), recordIds)
              )
            )
          )
          .records
          .map(_.id)
    }

  def getAll(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[String],
      start: Option[Int],
      limit: Option[Int]
  )(implicit session: DBSession): RecordsPage[RecordSummary] = {
    this.getSummaries(tenantId, opaQueries, pageToken, start, limit)
  }

  private def getSqlFromAspectQueries(
      aspectQueries: Iterable[AspectQuery],
      aspectOrQueries: Iterable[AspectQuery]
  ): Option[SQLSyntax] = {

    val orConditions = SQLSyntax.join(
      aspectOrQueries.map(aspectQueryToWhereClause(_)).toSeq,
      SQLSyntax.or
    )
    val andConditions = SQLSyntax.join(
      aspectQueries.map(aspectQueryToWhereClause(_)).toSeq,
      SQLSyntax.and
    )

    SQLSyntax.join(
      Seq(andConditions, orConditions).map {
        case sqlPart if sqlPart.value.nonEmpty =>
          // --- use () to wrap the `OR` or `AND` queries if they are not empty
          // --- SQLSyntax.roundBracket failed to check it
          SQLSyntax.roundBracket(sqlPart)
        case _ => SQLSyntax.empty
      },
      SQLSyntax.and
    ) match {
      // --- use () to wrap all conditions from AspectQuery
      case sqlPart if sqlPart.value.nonEmpty =>
        Some(SQLSyntax.roundBracket(sqlPart))
      case _ => None
    }
  }

  def getReadiness(
      logger: Option[LoggingAdapter] = None
  )(implicit session: DBSession): Option[Boolean] = {
    def checkTableExists(table: String): Boolean = {
      try {
        val schemaAndTable = "public." + table
        val regclass = sql"select to_regclass(${schemaAndTable})"

        val tableExists = regclass
          .map(_.toMap)
          .list
          .apply()
          .map { res =>
            {
              res("to_regclass").toString == table
            }
          }
          .forall(x => x);
        tableExists
      } catch {
        case e: Exception => {
          if (logger.isDefined) {
            logger.get.error(
              e,
              s"Error occurred on getReadiness probe for table ${table}"
            )
          }
          false
        }
      }
    }

    Some(
      List(
        checkTableExists("aspects"),
        checkTableExists("events"),
        checkTableExists("eventtypes"),
        checkTableExists("recordaspects"),
        checkTableExists("records"),
        checkTableExists("webhookevents"),
        checkTableExists("webhooks")
      ).forall(x => x)
    )
  }

  def getAllWithAspects(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      optionalAspectIds: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[Long] = None,
      start: Option[Int] = None,
      limit: Option[Int] = None,
      dereference: Option[Boolean] = None,
      aspectQueries: Iterable[AspectQuery] = Nil,
      aspectOrQueries: Iterable[AspectQuery] = Nil,
      orderBy: Option[OrderByDef] = None
  )(implicit session: DBSession): RecordsPage[Record] = {

    // --- make sure if orderBy is used, the involved aspectId is, at least, included in optionalAspectIds
    val notIncludedAspectIds = (orderBy
      .map(_.aspectName)
      .toList)
      .filter(!(aspectIds ++ optionalAspectIds).toList.contains(_))

    val selectors = Seq(
      getSqlFromAspectQueries(aspectQueries, aspectOrQueries)
    )

    this.getRecords(
      tenantId,
      aspectIds,
      optionalAspectIds ++ notIncludedAspectIds,
      opaRecordQueries,
      opaLinkedRecordQueries,
      pageToken,
      start,
      limit,
      dereference,
      selectors,
      orderBy
    )
  }

  // If a system tenant makes the request with aspectIds, it will always return 0.
  // Is this what we want?
  // See ticket https://github.com/magda-io/magda/issues/2360
  def getCount(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String],
      aspectQueries: Iterable[AspectQuery] = Nil,
      aspectOrQueries: Iterable[AspectQuery] = Nil
  )(implicit session: DBSession): Long = {

    val selectors = Seq(
      getSqlFromAspectQueries(aspectQueries, aspectOrQueries)
    )

    this.getCountInner(
      tenantId,
      opaQueries,
      aspectIds,
      selectors
    )
  }

  def getById(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      id: String
  )(implicit session: DBSession): Option[RecordSummary] = {
    this
      .getSummaries(tenantId, opaQueries, None, None, None, Some(id))
      .records
      .headOption
  }

  def getByIdWithAspects(
      tenantId: TenantId,
      id: String,
      recordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      linkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): Option[Record] = {
    this
      .getRecords(
        tenantId,
        aspectIds,
        optionalAspectIds,
        recordOpaQueries,
        linkedRecordQueries,
        None,
        None,
        Some(1),
        dereference,
        List(Some(sqls"recordId=$id"))
      )
      .records
      .headOption
  }

  def getByIdsWithAspects(
      tenantId: TenantId,
      ids: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): RecordsPage[Record] = {
    if (ids.isEmpty)
      RecordsPage(hasMore = false, Some("0"), List())
    else
      this.getRecords(
        tenantId,
        aspectIds,
        optionalAspectIds,
        opaRecordQueries,
        opaLinkedRecordQueries,
        None,
        None,
        None,
        dereference,
        List(Some(sqls"recordId in ($ids)"))
      )
  }

  def getRecordsLinkingToRecordIds(
      tenantId: TenantId,
      ids: Iterable[String],
      opaRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      opaLinkedRecordQueries: Option[List[(String, List[List[OpaQuery]])]],
      idsToExclude: Iterable[String] = Seq(),
      aspectIds: Iterable[String] = Seq(),
      optionalAspectIds: Iterable[String] = Seq(),
      dereference: Option[Boolean] = None
  )(implicit session: DBSession): RecordsPage[Record] = {
    val linkAspects =
      buildReferenceMap(List.concat(aspectIds, optionalAspectIds))
    if (linkAspects.isEmpty) {
      // There are no linking aspects, so there cannot be any records linking to these IDs.
      RecordsPage(hasMore = false, None, List())
    } else {
      val dereferenceSelectors = linkAspects.map {
        case (aspectId, propertyWithLink) =>
          sqls"""exists (select 1
                         from RecordAspects
                         where RecordAspects.recordId=Records.recordId
                         and aspectId=$aspectId
                         and ${SQLUtil.tenantIdToWhereClause(tenantId)}
                         and jsonb_exists_any(data->${propertyWithLink.propertyName}, ARRAY[$ids]))"""
      }

      val excludeSelector =
        if (idsToExclude.isEmpty) None
        else Some(sqls"recordId not in ($idsToExclude)")

      val selectors = Seq(
        Some(SQLSyntax.join(dereferenceSelectors.toSeq, SQLSyntax.or)),
        excludeSelector
      )

      this.getRecords(
        tenantId,
        aspectIds,
        optionalAspectIds,
        opaRecordQueries,
        opaLinkedRecordQueries,
        None,
        None,
        None,
        dereference,
        selectors
      )
    }
  }

  def getRecordAspectById(
      tenantId: TenantId,
      recordId: String,
      aspectId: String,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]]
  )(implicit session: DBSession): Option[JsObject] = {
    val opaSql =
      getOpaConditions(opaQueries, AuthOperations.read, defaultOpaPolicyId)

    sql"""select RecordAspects.aspectId as aspectId, Aspects.name as aspectName, data, RecordAspects.tenantId
          from RecordAspects
          inner join Aspects using (aspectId, tenantId)
          inner join Records using (recordId, tenantId)
where (RecordAspects.recordId, RecordAspects.aspectId)=($recordId, $aspectId) AND ${SQLUtil
      .tenantIdToWhereClause(
        tenantId,
        Some(SQLSyntax.createUnsafely("RecordAspects"))
      )}
	        and $opaSql
      """
      .map(rowToAspect)
      .single
      .apply()
  }

  def getPageTokens(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      limit: Option[Int] = None,
      recordSelector: Iterable[Option[SQLSyntax]] = Iterable()
  )(implicit session: DBSession): List[String] = {
    val recordsFilteredByTenantClause = filterRecordsByTenantClause(tenantId)

    val requiredAspectsAndOpaQueriesSelectors: Seq[Option[SQLSyntax]] =
      aspectIdsAndOpaQueriesToWhereParts(
        tenantId,
        aspectIds,
        opaQueries,
        AuthOperations.read
      ).toSeq.map(item => Option(item))

    val whereClauseParts
        : Seq[Option[SQLSyntax]] = recordSelector.toSeq ++ requiredAspectsAndOpaQueriesSelectors :+ Some(
      recordsFilteredByTenantClause
    )

    sql"""SELECT sequence
        FROM
        (
            SELECT sequence, ROW_NUMBER() OVER (ORDER BY sequence) AS rownum
            FROM records
            ${makeWhereClause(whereClauseParts)}
        ) AS t
        WHERE t.rownum % ${limit
      .map(l => Math.min(l, maxResultCount))
      .getOrElse(defaultResultCount)} = 0
        ORDER BY t.sequence;"""
      .map(rs => {
        rs.string("sequence")
      })
      .list
      .apply()
  }

  def putRecordById(
      tenantId: SpecifiedTenantId,
      id: String,
      newRecord: Record,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record] = {
    val newRecordWithoutAspects = newRecord.copy(aspects = Map())

    for {
      _ <- if (id == newRecord.id) Success(newRecord)
      else
        Failure(
          new ValidationException(
            "The provided ID does not match the record's ID."
          )
        )

      // --- validate aspects data against json schema
      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validateAspects(newRecord.aspects, tenantId)(
            session
          )
      }

      oldRecordWithoutAspects <- this.getByIdWithAspects(
        tenantId,
        id,
        None,
        None
      ) match {
        case Some(record) => Success(record)
        // Possibility of a race condition here. The record doesn't exist, so we try to create it.
        // But someone else could have created it in the meantime. So if our create fails, try one
        // more time to get an existing one. We use a nested transaction so that, if the create fails,
        // we don't end up with an extraneous record creation event in the database.
        case None =>
          // Check if record exists without any auth
          this.getById(tenantId, None, id) match {
            case Some(record) =>
              Failure(
                // TODO: Return a better error code.
                new RuntimeException(
                  s"You don't have permission to create this record $record."
                )
              )
            case None =>
              DB.localTx { nested =>
                // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
                // --- as the aspect data has been validated (unless not required) in the beginning of current method
                createRecord(tenantId, newRecord, userId, true)(nested).map(
                  _.copy(aspects = Map())
                )
              } match {
                case Success(record) => Success(record)
                case Failure(e) =>
                  this.getByIdWithAspects(
                    tenantId,
                    id,
                    None,
                    None
                  ) match {
                    case Some(record) => Success(record)
                    case None         => Failure(e)
                  }
              }
          }
      }
      recordPatch <- Try {
        // Diff the old record and the new one, ignoring aspects
        val oldRecordJson = oldRecordWithoutAspects.toJson
        val newRecordJson = newRecordWithoutAspects.toJson

        val diff = JsonDiff.diff(oldRecordJson, newRecordJson, remember = false)

        diff
      }

      // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
      // --- as the aspect data has been validated (unless not required) in the beginning of current method
      result <- patchRecordById(
        tenantId,
        id,
        recordPatch,
        userId,
        true
      )
      patchedAspects <- Try {
        newRecord.aspects.map {
          case (aspectId, data) =>
            (
              aspectId,
              // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
              // --- as the aspect data has been validated (unless not required) in the beginning of current method
              this.putRecordAspectById(
                tenantId,
                id,
                aspectId,
                data,
                userId,
                true
              )
            )
        }
      }
      // Report the first failed aspect, if any
      _ <- patchedAspects.find(_._2.isFailure) match {
        case Some((_, Failure(failure))) => Failure(failure)
        case _                           => Success(result)
      }
      // No failed aspects, so unwrap the aspects from the Success Trys.
      resultAspects <- Try {
        patchedAspects.mapValues(_.get)
      }
    } yield
      result.copy(aspects = resultAspects, sourceTag = newRecord.sourceTag)
  }

  def processRecordPatchOperationsOnAspects[T](
      recordPatch: JsonPatch,
      onReplaceAspect: (String, JsObject) => T,
      onPatchAspect: (String, JsonPatch) => T,
      onDeleteAspect: String => T
  ): Iterable[T] = {
    recordPatch.ops
      .groupBy(
        op =>
          op.path match {
            case "aspects" / (name / _) => Some(name)
            case _                      => None
          }
      )
      .filterKeys(_.isDefined)
      .map {
        // Create or patch each aspect.
        // We create if there's exactly one ADD operation and it's adding an entire aspect.
        case (
            Some(aspectId),
            List(Add("aspects" / (_ / rest), aValue))
            ) =>
          if (rest == Pointer.Empty)
            onReplaceAspect(
              aspectId,
              aValue.asJsObject
            )
          else
            onPatchAspect(
              aspectId,
              JsonPatch(Add(rest, aValue))
            )
        // We delete if there's exactly one REMOVE operation and it's removing an entire aspect.
        case (
            Some(aspectId),
            List(Remove("aspects" / (_ / rest), old))
            ) =>
          if (rest == Pointer.Empty) {
            onDeleteAspect(aspectId)
          } else {
            onPatchAspect(
              aspectId,
              JsonPatch(Remove(rest, old))
            )
          }
        // We patch in all other scenarios.
        case (Some(aspectId), operations) =>
          onPatchAspect(
            aspectId,
            JsonPatch(operations.map({
              // Make paths in operations relative to the aspect instead of the record
              case Add("aspects" / (_ / rest), aValue) =>
                Add(rest, aValue)
              case Remove("aspects" / (_ / rest), old) =>
                Remove(rest, old)
              case Replace("aspects" / (_ / rest), aValue, old) =>
                Replace(rest, aValue, old)
              case Move(
                  "aspects" / (sourceName / sourceRest),
                  "aspects" / (destName / destRest)
                  ) =>
                if (sourceName != destName)
                  // We can relax this restriction, and the one on Copy below, by turning a cross-aspect
                  // Move into a Remove on one and an Add on the other.  But it's probably not worth
                  // the trouble.
                  throw new RuntimeException(
                    "A patch may not move values between two different aspects."
                  )
                else
                  Move(sourceRest, destRest)
              case Copy(
                  "aspects" / (sourceName / sourceRest),
                  "aspects" / (destName / destRest)
                  ) =>
                if (sourceName != destName)
                  throw new RuntimeException(
                    "A patch may not copy values between two different aspects."
                  )
                else
                  Copy(sourceRest, destRest)
              case Test("aspects" / (_ / rest), aValue) =>
                Test(rest, aValue)
              case _ =>
                throw new RuntimeException(
                  "The patch contains an unsupported operation for aspect " + aspectId
                )
            }))
          )

        case _ =>
          throw new RuntimeException(
            "Aspect ID is missing (this shouldn't be possible)."
          )
      }
  }

  def patchRecordById(
      tenantId: SpecifiedTenantId,
      id: String,
      recordPatch: JsonPatch,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record] = {
    for {
      record <- this.getByIdWithAspects(tenantId, id, None, None) match {
        case Some(record) => Success(record)
        case None =>
          Failure(new RuntimeException("No record exists with that ID."))
      }

      // --- validate Aspect data against JSON schema
      // --- Check at the beginning to make sure no data is saved unless everything is valid
      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validateWithRecordPatch(recordPatch, id, tenantId)(
            session
          )
      }

      recordOnlyPatch <- Success(
        recordPatch.filter(
          op =>
            op.path match {
              case "aspects" / _ => false
              case _             => true
            }
        )
      )
      patchedRecord <- Try {
        recordOnlyPatch(record)
      }

      _ <- if (id == patchedRecord.id) Success(patchedRecord)
      else
        Failure(
          new RuntimeException("The patch must not change the record's ID.")
        )
      _ <- Try {
        // Sourcetag should not generate an event so updating it is done separately
        if (record.sourceTag != patchedRecord.sourceTag) {
          sql"""update Records set sourcetag = ${patchedRecord.sourceTag} where (recordId, tenantId) = ($id, ${tenantId.tenantId})""".update
            .apply()
        }
      }
      _ <- Try {
        // only update / generate event if name or authnReadPolicyId have changed. Id can't change, aspect changes are handled separately
        if ((record.name, record.authnReadPolicyId) != (patchedRecord.name, patchedRecord.authnReadPolicyId)) {
          val event =
            PatchRecordEvent(id, tenantId.tenantId, recordOnlyPatch).toJson.compactPrint
          val eventId =
            sql"insert into Events (eventTypeId, userId, tenantId, data) values (${PatchRecordEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $event::json)"
              .updateAndReturnGeneratedKey()
              .apply()
          sql"""update Records set name = ${patchedRecord.name}, authnReadPolicyId = ${patchedRecord.authnReadPolicyId}, lastUpdate = $eventId
                  where (recordId, tenantId) = ($id, ${tenantId.tenantId})""".update
            .apply()

          eventId
        } else {
          0
        }
      }
      aspectResults <- Try {
        // --- We have validate the Json Patch in the beginning. Thus, any aspect operations below should skip the validation
        processRecordPatchOperationsOnAspects(
          recordPatch,
          (aspectId: String, aspectData: JsObject) =>
            (
              aspectId,
              putRecordAspectById(
                tenantId,
                id,
                aspectId,
                aspectData,
                userId,
                true
              )
            ),
          (aspectId: String, aspectPatch: JsonPatch) =>
            (
              aspectId,
              patchRecordAspectById(
                tenantId,
                id,
                aspectId,
                aspectPatch,
                userId,
                true
              )
            ),
          (aspectId: String) => {
            deleteRecordAspect(tenantId, id, aspectId, userId)
            (aspectId, Success(JsNull))
          }
        )
      }
      // Report the first failed aspect, if any
      _ <- aspectResults.find(_._2.isFailure) match {
        case Some((_, failure)) => failure
        case _                  => Success(record)
      }
      // No failed aspects, so unwrap the aspects from the Success Trys.
      aspects <- Success(
        aspectResults
          .filter({
            case (_, Success(JsNull)) => false // aspect was deleted
            case _                    => true
          })
          .map(aspect => (aspect._1, aspect._2.get.asJsObject))
      )
    } yield
      Record(
        patchedRecord.id,
        patchedRecord.name,
        aspects.toMap,
        patchedRecord.authnReadPolicyId,
        tenantId = Some(tenantId.tenantId)
      )
  }

  def patchRecordAspectById(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      aspectPatch: JsonPatch,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject] = {
    for {
      aspect <- (this.getRecordAspectById(
        tenantId,
        recordId,
        aspectId,
        None
      ) match {
        case Some(aspect) => Success(aspect)
        // --- should not create a default empty aspect: empty aspect will likely be invalid if schema requires certain fields
        case None => Success(JsObject())
      })

      patchedAspect <- Try {
        aspectPatch(aspect).asJsObject
      }

      // --- validate Aspect data against JSON schema
      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validate(aspectId, patchedAspect, tenantId)(
            session
          )
      }

      testRecordAspectPatch <- Try {

        // Diff the old record aspect and the patched one to see whether an event should be created
        val oldAspectJson = aspect.toJson
        val newAspectJson = patchedAspect.toJson

        JsonDiff.diff(oldAspectJson, newAspectJson, remember = false)
      }

      eventId <- Try {
        if (testRecordAspectPatch.ops.nonEmpty) {
          val event = PatchRecordAspectEvent(
            recordId,
            tenantId.tenantId,
            aspectId,
            aspectPatch
          ).toJson.compactPrint
          sql"insert into Events (eventTypeId, userId, tenantId, data) values (${PatchRecordAspectEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $event::json)"
            .updateAndReturnGeneratedKey()
            .apply()
        } else {
          0
        }
      }

      _ <- Try {
        if (testRecordAspectPatch.ops.nonEmpty) {
          val jsonString = patchedAspect.compactPrint
          sql"""insert into RecordAspects (recordId, tenantId, aspectId, lastUpdate, data)
              values ($recordId, ${tenantId.tenantId}, $aspectId, $eventId, $jsonString::json)
              on conflict (aspectId, recordId, tenantId) do update
              set lastUpdate = $eventId, data = $jsonString::json
          """.update.apply()
        } else {
          0
        }
      }
    } yield patchedAspect
  }

  def putRecordAspectById(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      newAspect: JsObject,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject] = {
    for {
      // --- validate Aspect data against JSON schema
      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validate(aspectId, newAspect, tenantId)(
            session
          )
      }
      oldAspect <- this.getRecordAspectById(
        tenantId,
        recordId,
        aspectId,
        None
      ) match {
        case Some(aspect) => Success(aspect)
        // Possibility of a race condition here. The aspect doesn't exist, so we try to create it.
        // But someone else could have created it in the meantime. So if our create fails, try one
        // more time to get an existing one. We use a nested transaction so that, if the create fails,
        // we don't end up with an extraneous record creation event in the database.
        case None =>
          DB.localTx { nested =>
            // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
            // --- as the aspect data has been validated (unless not required) in the beginning of current method
            createRecordAspect(
              tenantId,
              recordId,
              aspectId,
              newAspect,
              userId,
              true
            )(nested)
          } match {
            case Success(aspect) => Success(aspect)
            case Failure(e) =>
              this.getRecordAspectById(
                tenantId,
                recordId,
                aspectId,
                None
              ) match {
                case Some(aspect) => Success(aspect)
                case None         => Failure(e)
              }
          }
      }
      recordAspectPatch <- Try {
        // Diff the old record aspect and the new one
        val oldAspectJson = oldAspect.toJson
        val newAspectJson = newAspect.toJson

        JsonDiff.diff(oldAspectJson, newAspectJson, remember = false)
      }
      // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
      // --- as the aspect data has been validated (unless not required) in the beginning of current method
      result <- patchRecordAspectById(
        tenantId,
        recordId,
        aspectId,
        recordAspectPatch,
        userId,
        true
      )
    } yield result
  }

  def createRecord(
      tenantId: SpecifiedTenantId,
      record: Record,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[Record] = {
    for {

      // --- validate aspects data against json schema
      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validateAspects(record.aspects, tenantId)(
            session
          )
      }

      eventId <- Try {
        val eventJson =
          CreateRecordEvent(
            record.id,
            tenantId.tenantId,
            record.name,
            record.authnReadPolicyId
          ).toJson.compactPrint
        sql"insert into Events (eventTypeId, userId, tenantId, data) values (${CreateRecordEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $eventJson::json)".updateAndReturnGeneratedKey
          .apply()
      }
      _ <- Try {
        sql"""insert into Records (recordId, tenantId, name, lastUpdate, sourcetag, authnreadpolicyid) values (${record.id}, ${tenantId.tenantId}, ${record.name}, $eventId, ${record.sourceTag}, ${record.authnReadPolicyId})""".update
          .apply()
      } match {
        case Failure(e: SQLException)
            if e.getSQLState.substring(0, 2) == "23" =>
          Failure(
            new RuntimeException(
              s"Cannot create record '${record.id}' because a record with that ID already exists."
            )
          )
        case anythingElse => anythingElse
      }

      // --- we never need to validate here (thus, set `forceSkipAspectValidation` = true)
      // --- as the aspect data has been validated (unless not required) in the beginning of current method
      hasAspectFailure <- record.aspects
        .map(
          aspect =>
            createRecordAspect(
              tenantId,
              record.id,
              aspect._1,
              aspect._2,
              userId,
              true
            )(session)
        )
        .find(_.isFailure) match {
        case Some(Failure(e)) => Failure(e)
        case _                => Success(record.copy(tenantId = Some(tenantId.tenantId)))
      }
    } yield hasAspectFailure
  }

  def deleteRecord(
      tenantId: SpecifiedTenantId,
      recordId: String,
      userId: String
  )(implicit session: DBSession): Try[Boolean] = {
    for {
      aspects <- Try {
        sql"select aspectId from RecordAspects where (recordId, tenantId)=($recordId, ${tenantId.tenantId})"
          .map(rs => rs.string("aspectId"))
          .list
          .apply()
      }
      _ <- aspects
        .map(
          aspectId => deleteRecordAspect(tenantId, recordId, aspectId, userId)
        )
        .find(_.isFailure) match {
        case Some(Failure(e)) => Failure(e)
        case _                => Success(aspects)
      }
      _ <- Try {
        val eventJson =
          DeleteRecordEvent(recordId, tenantId.tenantId).toJson.compactPrint
        sql"insert into Events (eventTypeId, userId, tenantId, data) values (${DeleteRecordEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $eventJson::json)".updateAndReturnGeneratedKey
          .apply()
      }
      rowsDeleted <- Try {
        sql"""delete from Records where (recordId, tenantId)=($recordId, ${tenantId.tenantId})""".update
          .apply()
      }
    } yield rowsDeleted > 0
  }

  def trimRecordsBySource(
      tenantId: SpecifiedTenantId,
      sourceTagToPreserve: String,
      sourceId: String,
      userId: String,
      logger: Option[LoggingAdapter] = None
  )(implicit session: DBSession): Try[Long] = {
    val recordIds = Try {
      sql"select distinct records.recordId, sourcetag from Records INNER JOIN recordaspects ON (records.recordid, records.tenantId) = (recordaspects.recordid, recordaspects.tenantId) where (sourcetag != $sourceTagToPreserve OR sourcetag IS NULL) and recordaspects.aspectid = 'source' and recordaspects.data->>'id' = $sourceId and Records.tenantId = ${tenantId.tenantId}"
        .map(rs => rs.string("recordId"))
        .list
        .apply()
    }

    val result = recordIds match {
      case Success(Nil) => Success(0L)
      case Success(ids) =>
        ids
          .map(recordId => deleteRecord(tenantId, recordId, userId))
          .foldLeft[Try[Long]](Success(0L))(
            (trySoFar: Try[Long], thisTry: Try[Boolean]) =>
              (trySoFar, thisTry) match {
                case (Success(countSoFar), Success(bool)) =>
                  Success(countSoFar + (if (bool) 1 else 0))
                case (Failure(err), _) => Failure(err)
                case (_, Failure(err)) => Failure(err)
              }
          )
      case Failure(err) => Failure(err)
    }

    result match {
      case Success(count: Long) =>
        if (logger.isDefined) {
          logger.get.info(s"Trimmed $count records.")
        }
        Success(count)
      case Failure(err) =>
        if (logger.isDefined) {
          logger.get.error(err, "Error happened when trimming records.")
        }
        Failure(err)
    }

  }

  def createRecordAspect(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      aspect: JsObject,
      userId: String,
      forceSkipAspectValidation: Boolean = false
  )(implicit session: DBSession): Try[JsObject] = {
    for {

      _ <- Try {
        if (!forceSkipAspectValidation)
          aspectValidator.validate(aspectId, aspect, tenantId)(session)
      }

      eventId <- Try {
        val eventJson = CreateRecordAspectEvent(
          recordId,
          tenantId.tenantId,
          aspectId,
          aspect
        ).toJson.compactPrint
        sql"insert into Events (eventTypeId, userId, tenantId, data) values (${CreateRecordAspectEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $eventJson::json)".updateAndReturnGeneratedKey
          .apply()
      }
      insertResult <- Try {
        val jsonData = aspect.compactPrint
        sql"""insert into RecordAspects (recordId, tenantId, aspectId, lastUpdate, data) values ($recordId, ${tenantId.tenantId}, $aspectId, $eventId, $jsonData::json)""".update
          .apply()
        aspect
      } match {
        case Failure(e: SQLException)
            if e.getSQLState.substring(0, 2) == "23" =>
          Failure(
            new RuntimeException(
              s"Cannot create aspect '$aspectId' for record '$recordId' because the record or aspect does not exist, or because data already exists for that combination of record and aspect."
            )
          )
        case anythingElse => anythingElse
      }
    } yield insertResult
  }

  def deleteRecordAspect(
      tenantId: SpecifiedTenantId,
      recordId: String,
      aspectId: String,
      userId: String
  )(implicit session: DBSession): Try[Boolean] = {
    for {
      _ <- Try {
        val eventJson =
          DeleteRecordAspectEvent(recordId, tenantId.tenantId, aspectId).toJson.compactPrint
        sql"insert into Events (eventTypeId, userId, tenantId, data) values (${DeleteRecordAspectEvent.Id}, ${userId}::uuid, ${tenantId.tenantId}, $eventJson::json)".updateAndReturnGeneratedKey
          .apply()
      }
      rowsDeleted <- Try {
        sql"""delete from RecordAspects where (aspectId, recordId, tenantId)=($aspectId, $recordId, ${tenantId.tenantId})""".update
          .apply()
      }
    } yield rowsDeleted > 0
  }

  def reconstructRecordFromEvents(
      id: String,
      events: Source[RegistryEvent, NotUsed],
      aspects: Iterable[String],
      optionalAspects: Iterable[String]
  ): Source[Option[Record], NotUsed] = {
    // TODO: can probably simplify some of this with lenses or whatever
    events
      .fold[JsValue](JsNull)(
        (recordValue, event) =>
          event.eventType match {
            case EventType.CreateRecord =>
              JsObject(
                "id" -> event.data.fields("recordId"),
                "name" -> event.data.fields("name"),
                "aspects" -> JsObject()
              )
            case EventType.PatchRecord =>
              event.data.fields("patch").convertTo[JsonPatch].apply(recordValue)
            case EventType.DeleteRecord => JsNull
            case EventType.CreateRecordAspect =>
              val createAspectEvent =
                event.data.convertTo[CreateRecordAspectEvent]
              val record = recordValue.asJsObject
              val existingFields = record.fields
              val existingAspects = record.fields("aspects").asJsObject.fields
              val newAspects = existingAspects + (createAspectEvent.aspectId -> createAspectEvent.aspect)
              val newFields = existingFields + ("aspects" -> JsObject(
                newAspects
              ))
              JsObject(newFields)
            case EventType.PatchRecordAspect =>
              val patchRecordAspectEvent =
                event.data.convertTo[PatchRecordAspectEvent]
              val record = recordValue.asJsObject
              val existingFields = record.fields
              val existingAspects = record.fields("aspects").asJsObject.fields
              val existingAspect =
                existingAspects(patchRecordAspectEvent.aspectId)
              val newAspects = existingAspects + (patchRecordAspectEvent.aspectId -> patchRecordAspectEvent.patch
                .apply(existingAspect))
              val newFields = existingFields + ("aspects" -> JsObject(
                newAspects
              ))
              JsObject(newFields)
            case EventType.DeleteRecordAspect =>
              val deleteRecordAspectEvent =
                event.data.convertTo[DeleteRecordAspectEvent]
              val record = recordValue.asJsObject
              val existingFields = record.fields
              val existingAspects = record.fields("aspects").asJsObject.fields
              val newAspects = existingAspects - deleteRecordAspectEvent.aspectId
              val newFields = existingFields + ("aspects" -> JsObject(
                newAspects
              ))
              JsObject(newFields)
            case _ => recordValue
          }
      )
      .map {
        case obj: JsObject => Some(obj.convertTo[Record])
        case _             => None
      }
  }

  def getPolicyIds(
      operation: AuthOperations.OperationType,
      recordIds: Option[Set[String]] = None
  )(implicit session: DBSession): Try[List[String]] = {
    if (recordIds.map(_.isEmpty) == Some(true)) {
      // No record ids = no policy ids
      Success(List())
    } else {
      val whereClause = recordIds match {
        case Some(recordIds) =>
          sqls"""WHERE ${SQLSyntax.joinWithOr(
            recordIds
              .map(recordId => sqls"""recordid = ${recordId}""")
              .toSeq: _*
          )}"""
        case _ => SQLSyntax.empty
      }

      val column = operation match {
        case AuthOperations.read =>
          SQLSyntax.createUnsafely("authnreadpolicyid")
        case _ =>
          throw new NotImplementedError(
            "Auth for operations other than read not yet implemented"
          )
      }

      Try {
        sql"""SELECT DISTINCT ${column}
      FROM records
      $whereClause
      """.map(
            rs =>
              // If the column is null, replace it with the default opa policy id
              rs.stringOpt(column).orElse(defaultOpaPolicyId).flatMap {
                // if policyId is empty string, treat it as Null as well
                case policyId: String if (policyId.trim != "") => Some(policyId)
                case _                                         => defaultOpaPolicyId
              }
          )
          .list()
          .apply()
          .flatten
      }
    }
  }

  def getLinkedRecordIds(
      operation: AuthOperations.OperationType,
      recordId: String,
      aspectIds: Iterable[String] = List()
  )(implicit session: DBSession): Try[Iterable[String]] = {

    /** For aspects that have links to other aspects, a map of the ids of those aspects to the location (first-level, not path) in their JSON where the link is */
    val referenceMap = this.buildReferenceMap(aspectIds)

    Try {
      (referenceMap
        .map {
          case (aspectId, property) =>
            val selectClause =
              if (property.isArray)
                sqls"jsonb_array_elements_text(data->${property.propertyName})"
              else sqls"data->>${property.propertyName}"

            sql"""SELECT DISTINCT $selectClause AS recordId
              FROM recordaspects
              WHERE aspectId = ${aspectId} AND recordId = ${recordId}"""
              .map(
                rs => rs.stringOpt("recordId").toList
              )
              .list()
              .apply()
        })
        .flatten
        .flatten
    }
  }

  // The current implementation assumes this API is not used by the system tenant.
  // Is this what we want?
  // See ticket https://github.com/magda-io/magda/issues/2359
  private def getSummaries(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[String],
      start: Option[Int],
      rawLimit: Option[Int],
      recordId: Option[String] = None
  )(implicit session: DBSession): RecordsPage[RecordSummary] = {
    val countWhereClauseParts =
      if (recordId.nonEmpty)
        Seq(
          recordId.map(
            id =>
              sqls"recordId=$id and ${SQLUtil.tenantIdToWhereClause(tenantId, Some(SQLSyntax.createUnsafely("Records")))}"
          )
        )
      else
        Seq(
          Some(
            sqls"${SQLUtil.tenantIdToWhereClause(tenantId, Some(SQLSyntax.createUnsafely("Records")))}"
          )
        )

    val opaConditions =
      getOpaConditions(opaQueries, AuthOperations.read, defaultOpaPolicyId)

    val whereClauseParts = countWhereClauseParts :+ Option(opaConditions) :+ pageToken
      .map(
        token =>
          sqls"Records.sequence > ${token.toLong} and ${SQLUtil
            .tenantIdToWhereClause(tenantId, Some(SQLSyntax.createUnsafely("Records")))}"
      )
    val limit = rawLimit.getOrElse(defaultResultCount)

    val result =
      sql"""select Records.sequence as sequence,
                   Records.recordId as recordId,
                   Records.name as recordName,
                   (select array_agg(aspectId) from RecordAspects where recordId = Records.recordId and ${SQLUtil
        .tenantIdToWhereClause(tenantId)}) as aspects,
                   Records.tenantId as tenantId
            from Records
            ${makeWhereClause(whereClauseParts)}
            order by sequence
            offset ${start.getOrElse(0)}
            limit ${limit + 1}"""
        .map(rs => {
          (rs.long("sequence"), rowToRecordSummary(rs))
        })
        .list
        .apply()

    val hasMore = result.length > limit
    val trimmed = result.take(limit)
    val lastSequence = if (hasMore) Some(trimmed.last._1) else None
    val pageResults = trimmed.map(_._2)

    RecordsPage[RecordSummary](
      lastSequence.isDefined,
      lastSequence.map(_.toString),
      pageResults
    )
  }

  private def getRecords(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      optionalAspectIds: Iterable[String],
      recordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      linkedRecordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      pageToken: Option[Long] = None,
      start: Option[Int] = None,
      rawLimit: Option[Int] = None,
      dereference: Option[Boolean] = None,
      recordSelector: Iterable[Option[SQLSyntax]] = Iterable(),
      orderBy: Option[OrderByDef] = None,
      maxLimit: Option[Int] = None
  )(implicit session: DBSession): RecordsPage[Record] = {

    if (orderBy.isDefined && pageToken.isDefined) {
      throw new Error(
        "`orderBy` and `pageToken` parameters cannot be both specified."
      )
    }

    val recordsFilteredByTenantClause: SQLSyntax = filterRecordsByTenantClause(
      tenantId
    )

    val theRecordSelector = recordSelector ++ Iterable(
      Some(recordsFilteredByTenantClause)
    )

    val requiredAspectsAndOpaQueriesSelectors: Seq[Option[SQLSyntax]] =
      aspectIdsAndOpaQueriesToWhereParts(
        tenantId,
        aspectIds,
        recordOpaQueries,
        AuthOperations.read
      ).toSeq.map(item => Option(item))

    val whereClauseParts: Seq[Option[SQLSyntax]] =
      (theRecordSelector.toSeq ++ requiredAspectsAndOpaQueriesSelectors) :+ pageToken
        .map(token => sqls"Records.sequence > $token")

    val aspectSelectors = aspectIdsToSelectClauses(
      tenantId,
      List.concat(aspectIds, optionalAspectIds), // aspectIds must come before optionalAspectIds.
      AuthOperations.read,
      recordOpaQueries,
      linkedRecordOpaQueries,
      dereference.getOrElse(false)
    )

    val limit = rawLimit
      .map(l => Math.min(l, maxLimit.getOrElse(maxResultCount)))
      .getOrElse(defaultResultCount)

    val rawTempName = ColumnNamePrefixType.PREFIX_TEMP.toString
    // Create unsafely so that column names will look like temp.aspect0 and temp.sequence.
    // Otherwise it will look like 'temp'.aspect0 and 'temp'.sequence.
    val tempName = SQLSyntax.createUnsafely(rawTempName)

    val orderBySql = orderBy match {
      case None =>
        SQLSyntax.orderBy(sqls"${tempName}.sequence")
      case Some(orderBy) =>
        orderBy.getSql(
          List.concat(aspectIds, optionalAspectIds),
          Some(rawTempName)
        )
    }

    val nonNullAspectsWhereClause =
      SQLSyntax.where(SQLSyntax.joinWithAnd(aspectIds.zipWithIndex.map {
        case (_, index) =>
          val name = getAspectColumnName(
            index,
            prefix = ColumnNamePrefixType.PREFIX_TEMP
          )
          sqls"$name is not null"
      }.toSeq: _*))

    val rawResult: SQL[Nothing, NoExtractor] =
      sql"""select * from (
              select Records.sequence as sequence,
                     Records.recordId as recordId,
                     Records.name as recordName,
                     Records.tenantId as tenantId,
                     Records.authnReadPolicyId as authnReadPolicyId
                     ${if (aspectSelectors.nonEmpty) sqls", $aspectSelectors"
      else SQLSyntax.empty},
                     Records.sourcetag as sourceTag
              from Records
              ${makeWhereClause(whereClauseParts)}
            ) as $tempName
            ${if (aspectIds.nonEmpty) sqls"$nonNullAspectsWhereClause"
      else SQLSyntax.empty}
            ${orderBySql}
            offset ${start.getOrElse(0)}
            limit ${limit + 1}
        """

    val result = rawResult
      .map(rs => {
        (
          rs.long("sequence"),
          rowToRecord(List.concat(aspectIds, optionalAspectIds))(rs)
        )
      })
      .list
      .apply()

    val hasMore = result.length > limit
    val trimmed = result.take(limit)
    val lastSequence = if (hasMore) Some(trimmed.last._1) else None
    val pageResults = trimmed.map(_._2)

    RecordsPage(hasMore, lastSequence.map(_.toString), pageResults)
  }

  private def getCountInner(
      tenantId: TenantId,
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      aspectIds: Iterable[String],
      recordSelector: Iterable[Option[SQLSyntax]] = Iterable()
  )(implicit session: DBSession): Long = {
    val recordsFilteredByTenantClause = filterRecordsByTenantClause(tenantId)
    val opaConditions =
      getOpaConditions(opaQueries, AuthOperations.read, defaultOpaPolicyId)
    val theRecordSelector = Iterable(Some(recordsFilteredByTenantClause)) ++ recordSelector ++ Iterable(
      Some(opaConditions)
    )
    val statement = if (aspectIds.size == 1) {
      // If there's only one aspect id, it's much much more efficient to query the recordaspects table rather than records.
      // Because a record cannot have more than one aspect for each type, counting the number of recordaspects with a certain aspect type
      // is equivalent to counting the records with that type
      val aspectIdsWhereClause = aspectIds
        .map(
          aspectId =>
            sqls"${SQLUtil.tenantIdToWhereClause(tenantId, Some(SQLSyntax.createUnsafely("RecordAspects")))} and RecordAspects.aspectId=$aspectId"
        )
        .toSeq
      val recordSelectorWhereClause = theRecordSelector.flatten
        .map(
          recordSelectorInner =>
            sqls"""EXISTS(
                     SELECT 1 FROM Records
                     WHERE ${SQLUtil
              .tenantIdToWhereClause(
                tenantId,
                Some(SQLSyntax.createUnsafely("Records"))
              )} AND RecordAspects.recordId=Records.recordId AND $recordSelectorInner)
              """
        )
        .toSeq
      val clauses =
        (aspectIdsWhereClause ++ recordSelectorWhereClause).map(Some.apply)
      sql"select count(*) from RecordAspects ${makeWhereClause(clauses)}"
    } else {
      // If there's zero or > 1 aspect ids involved then there's no advantage to querying record aspects instead.
      sql"select count(*) from Records ${makeWhereClause(aspectIdsToWhereClause(tenantId, aspectIds) ++ theRecordSelector)}"
    }

    statement.map(_.long(1)).single.apply().getOrElse(0L)
  }

  private def makeWhereClause(andParts: Seq[Option[SQLSyntax]]) = {
    andParts.filter(_.isDefined) match {
      case Seq() => SQLSyntax.empty
      case nonEmpty =>
        SQLSyntax.where(SQLSyntax.joinWithAnd(nonEmpty.map(_.get): _*))
    }
  }

  private def makeWhereParts(andParts: Seq[Option[SQLSyntax]]) = {
    andParts.filter(_.isDefined) match {
      case Seq() => SQLSyntax.empty
      case nonEmpty =>
        SQLSyntax.joinWithAnd(nonEmpty.map(_.get): _*)
    }
  }

  private def rowToRecordSummary(rs: WrappedResultSet): RecordSummary = {
    RecordSummary(
      rs.string("recordId"),
      rs.string("recordName"),
      rs.arrayOpt("aspects")
        .map(_.getArray().asInstanceOf[Array[String]].toList)
        .getOrElse(List()),
      rs.bigIntOpt("tenantId").map(BigInt.apply)
    )
  }

  private def rowToRecord(
      aspectIds: Iterable[String]
  )(rs: WrappedResultSet): Record = {
    Record(
      rs.string("recordId"),
      rs.string("recordName"),
      aspectIds.zipWithIndex
        .filter {
          case (_, index) => rs.stringOpt(s"aspect$index").isDefined
        }
        .map {
          case (aspectId, index) =>
            (aspectId, JsonParser(rs.string(s"aspect$index")).asJsObject)
        }
        .toMap,
      rs.stringOpt("authnReadPolicyId"),
      rs.stringOpt("sourceTag"),
      rs.bigIntOpt("tenantId").map(BigInt.apply)
    )
  }

  private def rowToAspect(rs: WrappedResultSet): JsObject = {
    JsonParser(rs.string("data")).asJsObject
  }

  def buildReferenceMap(
      aspectIds: Iterable[String]
  )(implicit session: DBSession): Map[String, PropertyWithLink] = {
    if (aspectIds.isEmpty) {
      Map()
    } else {
      val aspects: List[(String, Option[JsObject])] =
        sql"""select aspectId, jsonSchema
            from Aspects
            where aspectId in ($aspectIds)
          """
          .map(rs => {
            if (rs.stringOpt("jsonSchema").isDefined)
              (
                rs.string("aspectId"),
                Some(JsonParser(rs.string("jsonSchema")).asJsObject)
              )
            else
              (rs.string("aspectId"), None)
          })
          .list
          .apply()

      aspects
        .map {
          case (aspectId, jsonSchema) =>
            // This aspect can only have links if it uses hyper-schema
            if (jsonSchema.isDefined && jsonSchema.get.fields
                  .getOrElse("$schema", JsString(""))
                  .toString()
                  .contains("hyper-schema")) {
              // TODO: support multiple linked properties in an aspect.

              val properties = jsonSchema.get.fields
                .get("properties")
                .flatMap {
                  case JsObject(theProperties) => Some(theProperties)
                  case _                       => None
                }
                .getOrElse(Map())

              val propertyWithLinks = properties
                .map {
                  case (propertyName, property) =>
                    val linksInProperties =
                      property.extract[JsValue]('links.? / filter { value =>
                        val relPredicate = 'rel.is[String](_ == "item")
                        val hrefPredicate =
                          'href.is[String](_ == "/api/v0/registry/records/{$}")
                        relPredicate(value) && hrefPredicate(value)
                      })

                    val linksInItems = property.extract[JsValue](
                      'items.? / 'links.? / filter { value =>
                        val relPredicate = 'rel.is[String](_ == "item")
                        val hrefPredicate =
                          'href.is[String](_ == "/api/v0/registry/records/{$}")
                        relPredicate(value) && hrefPredicate(value)
                      }
                    )

                    if (linksInProperties.nonEmpty) {
                      Some(PropertyWithLink(propertyName, isArray = false))
                    } else if (linksInItems.nonEmpty) {
                      Some(PropertyWithLink(propertyName, isArray = true))
                    } else {
                      None
                    }
                }
                .filter(_.isDefined)
                .map(_.get)

              propertyWithLinks.map(property => (aspectId, property)).headOption
            } else {
              None
            }
        }
        .filter(_.isDefined)
        .map(_.get)
        .toMap
    }
  }

  private def aspectIdsAndOpaQueriesToWhereParts(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      opaQueries: Option[List[(String, List[List[OpaQuery]])]],
      operationType: AuthOperations.OperationType
  ): Iterable[SQLSyntax] = {
    val opaSql: SQLSyntax =
      getOpaConditions(opaQueries, operationType, defaultOpaPolicyId)

    val aspectWhereClauses: Seq[Option[SQLSyntax]] = aspectIdsToWhereClause(
      tenantId,
      aspectIds
    )
    val aspectWhereParts: SQLSyntax = makeWhereParts(aspectWhereClauses)
    val result = List(
      aspectWhereParts,
      opaSql
    )
    result
  }

  private def getAspectColumnName(
      index: Int,
      prefix: ColumnNamePrefixType.Value = ColumnNamePrefixType.PREFIX_EMPTY
  ): SQLSyntax = {
    val prefixStr =
      if (prefix == ColumnNamePrefixType.PREFIX_TEMP) prefix.toString + "."
      else prefix.toString
    // Use a simple numbered column name rather than trying to make the aspect name safe.
    SQLSyntax.createUnsafely(prefixStr + s"aspect$index")
  }

  private def aspectIdsToSelectClauses(
      tenantId: TenantId,
      aspectIds: Iterable[String],
      operationType: AuthOperations.OperationType,
      recordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      linkedRecordOpaQueries: Option[List[(String, List[List[OpaQuery]])]],
      dereference: Boolean = false
  )(implicit session: DBSession): Iterable[scalikejdbc.SQLSyntax] = {
    val outerOpaConditions =
      getOpaConditions(recordOpaQueries, operationType, defaultOpaPolicyId)
    val linkedRecordOpaConditions =
      getOpaConditions(
        linkedRecordOpaQueries,
        operationType,
        defaultOpaPolicyId
      )
    val referenceDetails =
      buildReferenceMap(aspectIds.toSeq)

    val result: Iterable[SQLSyntax] = aspectIds.zipWithIndex.map {
      case (aspectId, index) =>
        val aspectColumnName = getAspectColumnName(index)

        val selection = referenceDetails
          .get(aspectId)
          .map {
            case PropertyWithLink(propertyName, true) =>
              // The property is an array
              val linkedAspectsClause =
                if (dereference)
                  sqls"""
                    -- If a link exists in this aspect, then get every aspect from the linked record and bung it in
                    -- to the aspect data
                    CASE WHEN EXISTS (SELECT FROM jsonb_array_elements_text(RecordAspects.data->$propertyName)) THEN (
                      SELECT jsonb_set(
                        RecordAspects.data,
                        ${"{\"" + propertyName + "\"}"}::text[],
                        jsonb_agg(
                          jsonb_strip_nulls(
                            jsonb_build_object(
                              'id',
                              Records.recordId,
                              'name',
                              Records.name,
                              'authnReadPolicyId',
                              Records.authnReadPolicyId,
                              'aspects',
                              (
                                SELECT jsonb_object_agg(aspectId, data)
                                FROM RecordAspects
                                WHERE tenantId=Records.tenantId AND recordId=Records.recordId
                              )
                            )
                          )
                          order by ordinality
                        )
                      )
                      FROM Records
                      INNER JOIN jsonb_array_elements_text(RecordAspects.data->$propertyName)
                        WITH ordinality AS aggregatedId
                        ON aggregatedId.value=Records.recordId AND RecordAspects.tenantId=Records.tenantId
                      WHERE $linkedRecordOpaConditions
                    )
                    -- If there's no link in this aspect, just get the raw aspect data
                    ELSE (
                      select data from RecordAspects
                      where (aspectId, recordid, tenantId) = ($aspectId, Records.recordId, Records.tenantId)
                    )
                    END
                  """
                else
                  sqls"""
                    -- If the linked value exists, set the property to the id of the record it links to
                    CASE WHEN EXISTS (SELECT FROM jsonb_array_elements_text(RecordAspects.data->$propertyName)) THEN (
                      select jsonb_set(
                        RecordAspects.data,
                        ${"{\"" + propertyName + "\"}"}::text[],
                        jsonb_agg(Records.recordId)
                      )
                      from Records
                      inner join jsonb_array_elements_text(RecordAspects.data->$propertyName) as aggregatedId
                      on aggregatedId=Records.recordId and RecordAspects.tenantId=Records.tenantId
                      where $linkedRecordOpaConditions
                    )
                    -- If there's no linked value, just return the raw aspect data
                    ELSE (
                      select data from RecordAspects
                      where (aspectId, recordid, tenantId) = ($aspectId, Records.recordId, Records.tenantId)
                    )
                    END
                  """

              // Return the linked aspects if there are any, otherwise just return an empty array.
              sqls"""(
                SELECT COALESCE(
                  (SELECT ($linkedAspectsClause)),
                  (SELECT jsonb_set(RecordAspects.data, ${"{\"" + propertyName + "\"}"}::text[], '[]'::jsonb))
                )
              )"""
            case PropertyWithLink(propertyName, false) =>
              // The property is an object

              val linkedAspectsClause =
                if (dereference)
                  sqls"""
                    jsonb_set(
                      RecordAspects.data,
                      ${"{\"" + propertyName + "\"}"}::text[],
                      jsonb_strip_nulls(
                        jsonb_build_object(
                          'id', Records.recordId,
                          'name', Records.name,
                          'authnReadPolicyId', Records.authnReadPolicyId,
                          'aspects', (
                            SELECT jsonb_object_agg(aspectId, data)
                            FROM RecordAspects
                            WHERE tenantId=Records.tenantId AND recordId=Records.recordId AND $linkedRecordOpaConditions
                          )
                        )
                      )
                    )
                """
                else
                  sqls"""data"""

              val defaultClause =
                sqls"""SELECT jsonb_set(RecordAspects.data, ${"{\"" + propertyName + "\"}"}::text[], 'null'::jsonb)"""

              sqls"""(
                SELECT coalesce (
                  (
                    SELECT ($linkedAspectsClause)
                    FROM Records
                    WHERE Records.tenantId=RecordAspects.tenantId AND
                      Records.recordId=RecordAspects.data->>$propertyName
                      AND $linkedRecordOpaConditions
                  ),
                  (
                    ${defaultClause}
                  )
                )
              )"""
          }
          .getOrElse(sqls"data")

        sqls"""(select $selection from RecordAspects
              where (aspectId, recordid, tenantId)=($aspectId, Records.recordId, Records.tenantId)) as $aspectColumnName
          """
    }

    result
  }

  private def filterRecordsByTenantClause(tenantId: TenantId) = {
    tenantId match {
      case SpecifiedTenantId(inner) if (inner >= 0) =>
        sqls"Records.tenantId=${inner}"
      case AllTenantsId => sqls"true"
      case _            => throw new Exception("Invalid tenant value " + tenantId)
    }
  }

  private def aspectIdsToWhereClause(
      tenantId: TenantId,
      aspectIds: Iterable[String]
  ): Seq[Option[SQLSyntax]] = {
    aspectIds.map(aspectId => aspectIdToWhereClause(tenantId, aspectId)).toSeq
  }

  private def aspectIdToWhereClause(
      tenantId: TenantId,
      aspectId: String
  ) = {
    Some(
      sqls"""exists (
              select 1 from RecordAspects
              where (aspectId, recordid, tenantId)=($aspectId, Records.recordId, Records.tenantId))
        """
    )
  }

  private def aspectQueryToWhereClause(query: AspectQuery) = {
    val comparisonClause = aspectQueryToSql(query)

    val existClause = query match {
      case AspectQueryNotEqualValue(_, _, _) => sqls"NOT EXISTS"
      case _                                 => sqls"EXISTS"
    }

    sqls"""${existClause} (
             SELECT 1 FROM recordaspects
             WHERE (aspectId, recordid, tenantId)=(${query.aspectId}, Records.recordId, Records.tenantId) AND
             $comparisonClause )
      """
  }
}
