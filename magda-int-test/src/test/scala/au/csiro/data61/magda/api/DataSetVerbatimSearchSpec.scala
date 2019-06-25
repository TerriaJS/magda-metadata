package au.csiro.data61.magda.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.server.Route
import au.csiro.data61.magda.api.model.SearchResult
import au.csiro.data61.magda.model.misc._
import au.csiro.data61.magda.search.SearchStrategy.MatchAll
import au.csiro.data61.magda.test.util.Generators.randomCaseGen
import au.csiro.data61.magda.test.util.MagdaMatchers
import org.scalacheck.{Gen, Shrink}


class DataSetVerbatimSearchSpec extends DataSetSearchSpecBase {

  describe("quotes") {
    it("should be able to be found verbatim somewhere in a dataset") {
      implicit val stringShrink: Shrink[String] = Shrink { _ =>
        Stream.empty
      }

      implicit val disableShrink: Shrink[(List[DataSet], Route, String, String, DataSet)] = Shrink { _ =>
        Stream.empty
      }

      def validateQuote(quote: String): Boolean = {
        quote.forall(_.toInt >= 32) &&
          !quote.toLowerCase.contains("or") &&
          !quote.toLowerCase.contains("and") &&
          quote.exists(_.isLetterOrDigit)
      }

      val quoteGen = for {
        (future, dataSets) <- indexGen.suchThat(_._2.exists(_.description.isDefined))
        route = future.transform(tuple => tuple._3, throw new Exception("Error"))
        dataSetsWithDesc = dataSets.filter(_.description.exists(_.trim != ""))
        dataSet <- Gen.oneOf(dataSetsWithDesc)
        description = dataSet.description.get
        descWords = description.split(" ")
        start <- Gen.choose(0, Math.max(descWords.length - 1, 0))
        end <- Gen.choose(start + 1, descWords.length)
        quoteWords = descWords.slice(start, end)
        quote <- randomCaseGen(quoteWords.mkString(" ").trim) if validateQuote(quote)
        reverseOrderWords = quoteWords.reverse
        reverseOrderQuote <- randomCaseGen(reverseOrderWords.mkString(" ").trim)
      } yield (dataSetsWithDesc, route, quote, reverseOrderQuote, dataSet)

      forAll(quoteGen) {
        case (dataSets, routes, quote, reverseOrderQuote, sourceDataSet) =>
          routes.map(route => {
            assert(validateQuote(quote))
            assert(dataSets.nonEmpty)

            Get(s"""/v0/datasets?query=${encodeForUrl(s""""$quote"""")}&limit=${dataSets.length}""") ~> addSingleTenantIdHeader ~> route ~> check {
              status shouldBe OK
              val response = responseAs[SearchResult]

              response.strategy.get should equal(MatchAll)
              response.dataSets.isEmpty should be(false)

              response.dataSets.exists(_.identifier == sourceDataSet.identifier) should be(true)

              response.dataSets.foreach { dataSet =>
                withClue(s"dataSet term ${quote.toLowerCase} and dataSet ${dataSet.normalToString.toLowerCase}") {
                  MagdaMatchers.extractAlphaNum(dataSet.normalToString).contains(
                    MagdaMatchers.extractAlphaNum(quote)) should be(true)
                }
              }
            }

            Get(s"""/v0/datasets?query=${encodeForUrl(s""""$quote"""")}&limit=${dataSets.length}""") ~> addTenantIdHeader(tenant1) ~> route ~> check {
              status shouldBe OK
              val response = responseAs[SearchResult]
              response.dataSets.isEmpty should be(true)
            }

            // Just to make sure we're matching on the quote in order, run it backwards.
            Get(s"""/v0/datasets?query=${encodeForUrl(s""""$reverseOrderQuote"""")}&limit=${dataSets.length}""") ~> addSingleTenantIdHeader ~> route ~> check {
              status shouldBe OK
              val response = responseAs[SearchResult]

              if (response.strategy.get == MatchAll) {
                response.dataSets.foreach { dataSet =>
                  withClue(s"dataSet term ${reverseOrderQuote.toLowerCase} and dataSet ${dataSet.normalToString.toLowerCase}") {
                    MagdaMatchers.extractAlphaNum(dataSet.normalToString).contains(
                      MagdaMatchers.extractAlphaNum(reverseOrderQuote)) should be(true)
                  }
                }
              }
            }

            Get(s"""/v0/datasets?query=${encodeForUrl(s""""$reverseOrderQuote"""")}&limit=${dataSets.length}""") ~> addTenantIdHeader(tenant1) ~> route ~> check {
              status shouldBe OK
              val response = responseAs[SearchResult]
              response.hitCount shouldBe 0
            }
          })
      }
    }
  }
}
