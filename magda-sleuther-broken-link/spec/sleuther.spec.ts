import {} from "mocha";
import { expect } from "chai";
import * as sinon from "sinon";
import * as nock from "nock";
///<reference path="@magda/typescript-common/spec/jsverify.d.ts" />
import jsc = require("jsverify");
import Registry from "@magda/typescript-common/dist/Registry";
import { Record } from "@magda/typescript-common/dist/generated/registry/api";
import * as _ from "lodash";
import { onRecordFound, BrokenLinkAspect } from "../src/sleuther";
import {
  specificRecordArb,
  distStringsArb,
  distUrlArb,
  arrayOfSizeArb
} from "@magda/typescript-common/spec/arbitraries";
import { encodeURIComponentWithApost } from "@magda/typescript-common/spec/util";
import * as URI from "urijs";
const setupFtp = require("./setup-ftp");
const dns = require("dns");

const KNOWN_PROTOCOLS = ["https", "http", "ftp"];

describe("onRecordFound", function(this: Mocha.ISuiteCallbackContext) {
  this.timeout(10000);
  nock.disableNetConnect();
  const registryUrl = "http://example.com";
  let registryScope: nock.Scope;
  let ftp: any;

  before(() => {
    const originalDns = dns.lookup;

    sinon.stub(dns, "lookup").callsFake((hostname, options, callback) => {
      if (hostname.startsWith("ftp")) {
        callback(null, "127.0.0.1", 4);
      } else {
        originalDns(hostname, options, callback);
      }
    });

    sinon.stub(console, "info");

    ftp = setupFtp();

    nock.emitter.on("no match", onMatchFail);
  });

  const onMatchFail = (req: any) => {
    console.warn("Match failure: " + JSON.stringify(req.path));
  };

  after(() => {
    dns.lookup.restore();
    (console.info as any).restore();

    ftp.close();
    nock.emitter.removeListener("no match", onMatchFail);
  });

  const beforeEachProperty = () => {
    registryScope = nock(registryUrl); //.log(console.log);
  };

  const afterEachProperty = () => {
    nock.cleanAll();
  };

  const recordArb = (distUrlArb?: jsc.Arbitrary<String>) =>
    specificRecordArb(jsc)({
      "dataset-distributions": jsc.record({
        distributions: jsc.suchthat(
          jsc.array(
            specificRecordArb(jsc)({
              "dcat-distribution-strings": distStringsArb(jsc, distUrlArb)
            })
          ),
          (arr: Record[]) => {
            const ids = arr.map(_ => _.id);

            return _.isEqual(ids, _.uniq(ids));
          }
        )
      })
    });

  const defaultRecordArb = recordArb();

  function urlsFromRecord(record: Record): string[] {
    return _(record.aspects["dataset-distributions"].distributions)
      .map((dist: any) => dist.aspects["dcat-distribution-strings"])
      .flatMap(_.values)
      .filter(x => !!x)
      .value();
  }

  const recordArbWithSuccesses: jsc.Arbitrary<{
    record: Record;
    successes: object;
  }> = jsc.bless({
    generator: defaultRecordArb.generator.flatmap(record => {
      const urls: any[] = _(urlsFromRecord(record))
        .filter(url => KNOWN_PROTOCOLS.indexOf(URI(url).scheme()) >= 0)
        .uniq()
        .value();

      const gens: jsc.Generator<boolean>[] = urls.map(() => jsc.bool.generator);

      const gen = jsc.generator.tuple(gens);

      return gen.map(successfulArr => {
        const successes = urls.reduce((soFar, current, index) => {
          soFar[current] = successfulArr[index];
          return soFar;
        }, {});

        return { record, successes };
      });
    }),

    show: ({ record, successes }: { record: Record; successes: object }) =>
      defaultRecordArb.show(record) + " and " + JSON.stringify(successes),

    shrink: jsc.shrink.bless(
      ({ record, successes }: { record: Record; successes: object }) => {
        const records = defaultRecordArb.shrink(record);

        return records.map(record => {
          const urls = _.uniq(urlsFromRecord(record));

          const after = {
            record,
            successes: _.pickBy(
              successes,
              (value: boolean, key: string) => urls.indexOf(key) >= 0
            )
          };

          return after;
        });
      }
    )
  });

  const recordArbWithSuccessesNoDupFtpPaths = jsc.suchthat(
    recordArbWithSuccesses,
    (result: any) => {
      const ftpPaths = _(
        result.record.aspects["dataset-distributions"].distributions
      )
        .map((dist: any) => dist.aspects["dcat-distribution-strings"])
        .flatMap(_.values)
        .filter(x => !!x)
        .filter((x: string) => x.startsWith("ftp"))
        .map(url => new URI(url).path())
        .value();

      // console.log(
      //   "FTP Paths: " +
      //     _.uniq(ftpPaths) +
      //     "|" +
      //     ftpPaths +
      //     "... " +
      //     _.isEqual(_.uniq(ftpPaths), ftpPaths)
      // );

      return _.isEqual(_.uniq(ftpPaths), ftpPaths);
    }
  );

  it("Should correctly record link statuses and quality", function() {
    return jsc.assert(
      jsc.forall(
        recordArbWithSuccessesNoDupFtpPaths,
        ({
          record,
          successes
        }: {
          record: Record;
          successes: { [x: string]: boolean };
        }) => {
          beforeEachProperty();

          ftp.successes = _(successes)
            .pickBy((value, url) => url.startsWith("ftp"))
            .mapKeys((value: boolean, url: string) => new URI(url).path())
            .value();

          const allDists =
            record.aspects["dataset-distributions"].distributions;

          const allDistStrings = _(allDists)
            .map((dist: any) => dist.aspects["dcat-distribution-strings"])
            .value();

          const httpDistUrls = _(allDistStrings)
            .flatMap(_.values)
            .filter(x => !!x)
            .filter((x: string) => x.startsWith("http"))
            .map((url: string) => ({
              url,
              success: successes[url]
            }))
            .value();

          const distScopes = httpDistUrls.map(
            ({ url, success }: { url: string; success: boolean }) =>
              nock(url)
                .head(url.endsWith("/") ? "/" : "")
                .reply(success ? 200 : 404)
          );

          const results = allDists.map((dist: Record) => {
            const { downloadURL, accessURL } = dist.aspects[
              "dcat-distribution-strings"
            ];
            const success = successes[downloadURL] || successes[accessURL];

            const isUnknownProtocol = (url: string) => {
              if (!url) {
                return false;
              }
              const scheme = URI(url).scheme();
              return !scheme || KNOWN_PROTOCOLS.indexOf(scheme) === -1;
            };

            const downloadUnknown = isUnknownProtocol(downloadURL);
            const accessUnknown = isUnknownProtocol(accessURL);

            // console.log(
            //   "unknowns: " +
            //     downloadURL +
            //     ":" +
            //     downloadUnknown +
            //     "/" +
            //     accessURL +
            //     ":" +
            //     accessUnknown
            // );

            // console.log(
            //   `expecting PUT /records/${encodeURIComponentWithApost(
            //     dist.id
            //   )}/aspects/source-link-status` +
            //     ": " +
            //     (success ? "active" : "broken")
            // );

            const result = success
              ? "active"
              : downloadUnknown || accessUnknown ? "unknown" : "broken";

            registryScope
              .put(
                `/records/${encodeURIComponentWithApost(
                  dist.id
                )}/aspects/source-link-status`,
                (body: BrokenLinkAspect) => {
                  const statusMatch = body.status === result;

                  const codeMatch = ((code?: number) => {
                    if (
                      (successes[downloadURL] &&
                        downloadURL.startsWith("http")) ||
                      (!successes[downloadURL] &&
                        successes[accessURL] &&
                        accessURL.startsWith("http"))
                    ) {
                      return code === 200;
                    } else if (
                      result === "broken" &&
                      ((downloadURL && downloadURL.startsWith("http")) ||
                        (!downloadURL &&
                          accessURL &&
                          accessURL.startsWith("http")))
                    ) {
                      return code === 404;
                    } else {
                      return _.isUndefined(code);
                    }
                  })(body.httpStatusCode);

                  const errorMatch = ((arg?: Error) =>
                    success ? _.isUndefined(arg) : !_.isUndefined(arg))(
                    body.errorDetails
                  );

                  return statusMatch && codeMatch && errorMatch;
                }
              )
              .reply(201);

            return result;
          });

          if (allDists.length > 0) {
            registryScope
              .patch(
                `/records/${encodeURIComponentWithApost(
                  record.id
                )}/aspects/dataset-quality-rating`,
                [
                  {
                    op: "add",
                    path: "/source-link-status",
                    value: {
                      score:
                        results.filter((result: string) => result === "active")
                          .length / allDists.length,
                      weighting: 1
                    }
                  }
                ]
              )
              .reply(201);
          }

          const registry = new Registry({
            baseUrl: registryUrl,
            maxRetries: 0
          });

          return onRecordFound(registry, record, 0, 0)
            .then(() => {
              distScopes.forEach(scope => scope.done());
              registryScope.done();
            })
            .then(() => {
              afterEachProperty();
              return true;
            })
            .catch(e => {
              afterEachProperty();
              throw e;
            });
        }
      ),
      {
        // rngState: "8848e1b5026fcad793",
        tests: 1000
        // quiet: false
      }
    );
  });

  const httpOnlyRecordArb = jsc.suchthat(
    defaultRecordArb,
    (record: Record) =>
      record.aspects["dataset-distributions"].distributions.length > 1 &&
      record.aspects[
        "dataset-distributions"
      ].distributions.every((dist: any) => {
        const aspect = dist.aspects["dcat-distribution-strings"];

        const definedURLs = [aspect.accessURL, aspect.downloadURL].filter(
          x => !!x
        );

        return (
          definedURLs.length > 0 && definedURLs.every(x => x.startsWith("http"))
        );
      })
  );

  const failureCodeArb = jsc.suchthat(
    jsc.integer(300, 600),
    int => int !== 429
  );

  const failureCodesArb = jsc.nearray(failureCodeArb);

  function arbFlatMap<T, U>(
    arb: jsc.Arbitrary<T>,
    arbForward: (t: T) => jsc.Arbitrary<U>,
    shrinker: (t: T, u: U) => U[],
    show: (arr: [T, U]) => string = arr => arr.toString()
  ): jsc.Arbitrary<[T, U]> {
    return jsc.bless<[T, U]>({
      generator: arb.generator.flatmap((t: T) => {
        return arbForward(t).generator.map(
          (result: U) => [t, result] as [T, U]
        );
      }),
      show,
      shrink: jsc.shrink.bless(x => {
        const t: T = x[0];
        const u: U = x[1];
        const y: [T, U][] = arb.shrink(t).map((smallT: T) => {
          return _.flatMap(
            shrinker(smallT, u),
            (smallU: U) => [smallT, smallU] as [T, U]
          );
        }) as [T, U][];

        return y;
      })
    });
  }

  describe("retrying", () => {
    const retrySpec = (caption: string, success: boolean) => {
      it(caption, function() {
        const retryArb = jsc.integer(0, 10);

        const failuresArb = arbFlatMap(
          retryArb,
          retryCount =>
            arrayOfSizeArb(
              jsc,
              success ? retryCount : retryCount + 1,
              failureCodeArb
            ),
          (newRetryCount, oldFailureCodes: number[]) => {
            const newFailureLengthCount = success
              ? newRetryCount
              : newRetryCount + 1;
            const newFailureCodes: number[][] = [];
            for (
              let i = 0;
              i <= oldFailureCodes.length - newFailureLengthCount;
              i++
            ) {
              newFailureCodes.push(
                _.slice(oldFailureCodes, i, i + newFailureLengthCount)
              );
            }
            return newFailureCodes;
          }
        );

        return jsc.assert(
          jsc.forall(
            httpOnlyRecordArb,
            failuresArb,
            (record: Record, [retryCount, failureCodes]) => {
              beforeEachProperty();

              const registry = new Registry({
                baseUrl: registryUrl,
                maxRetries: 0
              });

              const distScopes = urlsFromRecord(record).map(url => {
                const scope = nock(url); //.log(console.log);

                failureCodes.forEach(failureCode => {
                  scope.head(url.endsWith("/") ? "/" : "").reply(failureCode);
                });

                if (success) {
                  scope.head(url.endsWith("/") ? "/" : "").reply(200);
                }

                return scope;
              });

              const allDists =
                record.aspects["dataset-distributions"].distributions;

              allDists.forEach((dist: Record) => {
                registryScope
                  .put(
                    `/records/${encodeURIComponentWithApost(
                      dist.id
                    )}/aspects/source-link-status`,
                    (result: any) => {
                      const statusMatch =
                        result.status === (success ? "active" : "broken");
                      const codeMatch =
                        result.httpStatusCode ===
                        (success ? 200 : _.last(failureCodes));

                      return statusMatch && codeMatch;
                    }
                  )
                  .reply(201);
              });

              if (allDists.length > 0) {
                // console.log(
                //   `/records/${encodeURIComponentWithApost(
                //     record.id
                //   )}/aspects/dataset-quality-rating`
                // );
                registryScope
                  .patch(
                    `/records/${encodeURIComponentWithApost(
                      record.id
                    )}/aspects/dataset-quality-rating`,
                    [
                      {
                        op: "add",
                        path: "/source-link-status",
                        value: {
                          score: success ? 1 : 0,
                          weighting: 1
                        }
                      }
                    ]
                  )
                  .reply(201);
              }

              return onRecordFound(registry, record, 0, retryCount)
                .then(() => {
                  registryScope.done();
                  distScopes.forEach(scope => scope.done());
                })
                .then(() => {
                  afterEachProperty();
                  return true;
                })
                .catch(e => {
                  afterEachProperty();
                  throw e;
                });

              // promise.catch(() => {}).then(afterEachProperty);
            }
          ),
          {
            rngState: "828caf026d4be91573"
            // tests: 1000
            // quiet: false
          }
        );
      });
    };

    retrySpec("Should result in success if the last retry is successful", true);
    retrySpec(
      "Should result in failures if the max number of retries is exceeded",
      false
    );
  });

  it("Should only try to make one request per host at a time", function() {
    const httpOrHttps = jsc.sampler(
      jsc.oneof([jsc.constant("http"), jsc.constant("https")])
    )(1);

    const urlArb = distUrlArb(jsc, {
      schemeArb: jsc.constant(httpOrHttps),
      hostArb: jsc.oneof([
        jsc.constant("example1"),
        jsc.constant("example2"),
        jsc.constant("example3")
      ])
    });

    const thisRecordArb = jsc.suchthat(recordArb(urlArb), record => {
      const urls: string[] = urlsFromRecord(record);
      const hosts: string[] = urls.map(url => {
        const uri = new URI(url);

        return uri.scheme() + "://" + uri.host();
      });

      return !_.isEqual(_.uniq(hosts), hosts);
    });

    return jsc.assert(
      jsc.forall(
        thisRecordArb,
        failureCodesArb,
        jsc.integer(0, 100),
        (record: Record, failures: number[], delayMs: number) => {
          beforeEachProperty();

          const registry = new Registry({
            baseUrl: registryUrl,
            maxRetries: 0
          });

          const distScopes = urlsFromRecord(
            record
          ).reduce((scopeLookup, url) => {
            const uri = new URI(url);
            const base = uri.scheme() + "://" + uri.host();

            if (!scopeLookup[base]) {
              scopeLookup[base] = nock(base);
            }

            const scope = scopeLookup[base];

            failures.forEach(failureCode =>
              scope.head(uri.path()).delay(delayMs).reply(failureCode)
            );

            scope.head(uri.path()).delay(delayMs).reply(200);
            return scopeLookup;
          }, {} as { [host: string]: nock.Scope });

          _.forEach(distScopes, (scope: nock.Scope, host: string) => {
            let countForThisScope = 0;

            scope.on("request", () => {
              countForThisScope++;
              expect(countForThisScope).to.equal(1);
            });

            scope.on("replied", () => {
              countForThisScope--;
              expect(countForThisScope).to.equal(0);
            });
          });

          const allDists =
            record.aspects["dataset-distributions"].distributions;

          registryScope.patch(/.*/).reply(201);
          registryScope.put(/.*/).times(allDists.length).reply(201);

          return onRecordFound(registry, record, 0, failures.length)
            .then(() => {
              _.values(distScopes).forEach(scope => scope.done());
            })
            .then(() => {
              afterEachProperty();
              return true;
            })
            .catch(e => {
              afterEachProperty();
              throw e;
            });
        }
      ),
      {
        tests: 10
      }
    );
  });

  const emptyRecordArb = jsc.oneof([
    specificRecordArb(jsc)({
      "dataset-distributions": jsc.constant(undefined)
    }),
    specificRecordArb(jsc)({
      "dataset-distributions": jsc.record({
        distributions: jsc.constant([])
      })
    })
  ]);

  jsc.property(
    "Should do nothing if no distributions",
    emptyRecordArb,
    record => {
      beforeEachProperty();

      const registry = new Registry({
        baseUrl: registryUrl
      });

      return onRecordFound(registry, record).then(() => {
        afterEachProperty();

        registryScope.done();
        return true;
      });
    }
  );
});
