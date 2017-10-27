import * as moment from "moment";
import * as URI from "urijs";
import * as yargs from "yargs";

import Registry from "@magda/typescript-common/dist/registry/AuthorizedRegistryClient";
import addJwtSecretFromEnvVar from "@magda/typescript-common/dist/session/addJwtSecretFromEnvVar";

import organizationAspectBuilders from "./organizationAspectBuilders";
import datasetAspectBuilders from "./datasetAspectBuilders";
import distributionAspectBuilders from "./distributionAspectBuilders";
import ProjectOpenDataConnector from "./ProjectOpenDataConnector";

const argv = addJwtSecretFromEnvVar(
    yargs
        .config()
        .help()
        .option("name", {
            describe:
                "The name of this connector, to be displayed to users to indicate the source of datasets.",
            type: "string",
            demandOption: true
        })
        .option("sourceUrl", {
            describe: "The URL of the data.json file.",
            type: "string",
            demandOption: true
        })
        .option("registryUrl", {
            describe:
                "The base URL of the registry to which to write data from CSW.",
            type: "string",
            default: "http://localhost:6101/v0"
        })
        .option("jwtSecret", {
            describe: "The shared secret for intra-network communication",
            type: "string"
        })
        .option("userId", {
            describe:
                "The user id to use when making authenticated requests to the registry",
            type: "string",
            demand: true,
            default:
                process.env.USER_ID || process.env.npm_package_config_userId
        }).argv
);

const registry = new Registry({
    baseUrl: argv.registryUrl,
    jwtSecret: argv.jwtSecret,
    userId: argv.userId
});

const connector = new ProjectOpenDataConnector({
    name: argv.name,
    url: argv.sourceUrl,
    source: null,
    registry: registry,
    libraries: {
        moment: moment,
        URI: URI
    },
    organizationAspectBuilders: organizationAspectBuilders,
    datasetAspectBuilders: datasetAspectBuilders,
    distributionAspectBuilders: distributionAspectBuilders
});

connector.run().then(result => {
    console.log(result.summarize());
});
