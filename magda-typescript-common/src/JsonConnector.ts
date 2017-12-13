import { AspectDefinition, Record } from './generated/registry/api';
import AsyncPage, { forEachAsync, asyncPageToArray } from './AsyncPage';
import ConnectionResult from './ConnectionResult';
import CreationFailure from './CreationFailure';
import JsonTransformer from './JsonTransformer';
import Registry from './registry/AuthorizedRegistryClient';
import * as express from 'express';
import * as fs from 'fs';
import * as path from 'path';
import * as process from 'process';
import { Moment } from 'moment';

/**
 * A base class for connectors for most any JSON-based catalog source.
 */
export default class JsonConnector {
    public readonly source: ConnectorSource;
    public readonly transformer: JsonTransformer;
    public readonly registry: Registry;
    public readonly maxConcurrency: number;

    constructor({
        source,
        transformer,
        registry,
        maxConcurrency = 6
    }: JsonConnectorOptions) {
        this.source = source;
        this.transformer = transformer;
        this.registry = registry;
        this.maxConcurrency = maxConcurrency;
    }

    async createAspectDefinitions(): Promise<ConnectionResult> {
        const result = new ConnectionResult();

        const allAspectDefinitions = this.transformer.getRequiredAspectDefinitions();

        const aspectBuilderPage = AsyncPage.single<AspectDefinition[]>(allAspectDefinitions);
        await forEachAsync(aspectBuilderPage, this.maxConcurrency, async aspectDefinition => {
            const aspectDefinitionOrError = await this.registry.putAspectDefinition(aspectDefinition);
            if (aspectDefinitionOrError instanceof Error) {
                result.aspectDefinitionFailures.push(new CreationFailure(aspectDefinition.id, undefined, aspectDefinitionOrError));
            } else {
                ++result.aspectDefinitionsConnected;
            }
        });

        return result;
    }

    async createOrganization(organizationJson: object): Promise<Record | Error> {
        return this.registry.putRecord(this.transformer.organizationJsonToRecord(organizationJson));
    }

    async createDataset(datasetJson: object): Promise<Record | Error> {
        return this.registry.putRecord(this.transformer.datasetJsonToRecord(datasetJson));
    }

    async createDistribution(distributionJson: object, datasetJson: object): Promise<Record | Error> {
        return this.registry.putRecord(this.transformer.distributionJsonToRecord(distributionJson, datasetJson));
    }

    async createOrganizations(): Promise<ConnectionResult> {
        const result = new ConnectionResult();

        if (this.source.hasFirstClassOrganizations) {
            const organizations = this.source.getJsonFirstClassOrganizations();
            await forEachAsync(organizations, this.maxConcurrency, async organization => {
                const recordOrError = await this.createOrganization(organization);
                if (recordOrError instanceof Error) {
                    result.organizationFailures.push(new CreationFailure(
                        this.transformer.getIdFromJsonOrganization(organization),
                        undefined,
                        recordOrError));
                } else {
                    ++result.organizationsConnected;
                }
            });
        }

        return result;
    }

    async createDatasetsAndDistributions(): Promise<ConnectionResult> {
        const result = new ConnectionResult();

        const datasets = this.source.getJsonDatasets();
        await forEachAsync(datasets, this.maxConcurrency, async dataset => {
            const record = this.transformer.datasetJsonToRecord(dataset);

            const distributions = this.source.getJsonDistributions(dataset.dataset);
            if (distributions) {
                const distributionIds: string[] = [];
                await forEachAsync(distributions, 1, async distribution => {
                    const recordOrError = await this.createDistribution(distribution, dataset);
                    if (recordOrError instanceof Error) {
                        result.distributionFailures.push(new CreationFailure(
                            this.transformer.getIdFromJsonDistribution(distribution, dataset),
                            this.transformer.getIdFromJsonDataset(dataset),
                            recordOrError));
                    } else {
                        ++result.distributionsConnected;
                        distributionIds.push(this.transformer.getIdFromJsonDistribution(distribution, dataset));
                    }
                });

                record.aspects['dataset-distributions'] = {
                    distributions: distributionIds
                };
            }

            if (this.source.hasFirstClassOrganizations) {
                const publisher = this.source.getJsonDatasetPublisherId(dataset);
                if (publisher) {
                    record.aspects['dataset-publisher'] = {
                        publisher: publisher
                    };
                }
            } else {
                const publisher = await this.source.getJsonDatasetPublisher(dataset);
                const publisherId = this.transformer.getIdFromJsonOrganization(publisher);

                const recordOrError = await this.createOrganization(publisher);
                if (recordOrError instanceof Error) {
                    result.organizationFailures.push(new CreationFailure(
                        publisherId,
                        undefined,
                        recordOrError));
                } else {
                    record.aspects['dataset-publisher'] = {
                        publisher: publisherId
                    };
                    ++result.organizationsConnected;
                }
            }

            const recordOrError = await this.registry.putRecord(record);
            if (recordOrError instanceof Error) {
                result.datasetFailures.push(new CreationFailure(
                    this.transformer.getIdFromJsonDataset(dataset),
                    undefined,
                    recordOrError));
            } else {
                ++result.datasetsConnected;
            }
        });

        return result;
    }

    /**
     * Runs the connector, creating aspect definitions, organizations, datasets, and distributions in the
     * registry as necessary.
     *
     * @returns {Promise<ConnectionResult>}
     * @memberof JsonConnector
     */
    async run(): Promise<ConnectionResult> {
        const aspectResult = await this.createAspectDefinitions();
        const organizationResult = await this.createOrganizations();
        const datasetAndDistributionResult = await this.createDatasetsAndDistributions();
        return ConnectionResult.combine(aspectResult, organizationResult, datasetAndDistributionResult);
    }

    runInteractive(options: JsonConnectorRunInteractiveOptions) {
        const transformerForBrowserPath = path.resolve(process.cwd(), 'dist', 'createTransformerForBrowser.js');
        if (!fs.existsSync(transformerForBrowserPath)) {
            throw new Error('Cannot run this connector in interactive mode because dist/createTransformerForBrowser.js does not exist.');
        }

        var app = express();
        app.use(require("body-parser").json());

        if (options.timeoutSeconds > 0) {
            this.shutdownOnIdle(app, options.timeoutSeconds);
        }

        app.get('/v0/status', (req, res) => {
            res.send('OK');
        });

        app.get('/v0/config', (req, res) => {
            res.send(options.transformerOptions);
        });

        app.get('/v0/datasets/:id', (req, res) => {
            this.source.getJsonDataset(req.params.id).then(function(dataset) {
                res.send(dataset);
            });
        });

        app.get('/v0/datasets/:id/distributions', (req, res) => {
            this.source.getJsonDataset(req.params.id).then(dataset => {
                return asyncPageToArray(this.source.getJsonDistributions(dataset)).then(distributions => {
                    res.send(distributions);
                })
            });
        });

        app.get('/v0/datasets/:id/publisher', (req, res) => {
            this.source.getJsonDataset(req.params.id).then(dataset => {
                return this.source.getJsonDatasetPublisher(dataset).then(publisher => {
                    res.send(publisher);
                })
            });
        });

        app.get('/v0/search/datasets', (req, res) => {
            asyncPageToArray(this.source.searchDatasetsByTitle(req.query.title, 10)).then(datasets => {
                res.send(datasets);
            });
        });

        if (this.source.hasFirstClassOrganizations) {
            app.get('/v0/organizations/:id', (req, res) => {
                this.source.getJsonFirstClassOrganization(req.params.id).then(function(organization) {
                    res.send(organization);
                });
            });

            app.get('/v0/search/organizations', (req, res) => {
                asyncPageToArray(this.source.searchFirstClassOrganizationsByTitle(req.query.title, 5)).then(organizations => {
                    res.send(organizations);
                });
            });
            }

        app.get('/v0/test-harness.js', function(req, res) {
          res.sendFile(transformerForBrowserPath);
        });

        app.listen(options.listenPort);
        console.log(`Listening on port ${options.listenPort}.`);
    }

    private shutdownOnIdle(express: express.Express, timeoutSeconds: number) {
        // Arrange to shut down the Express server after the idle timeout expires.
        let timeoutId: NodeJS.Timer;

        function resetTimeout() {
            if (timeoutId !== undefined) {
                clearTimeout(timeoutId);
            }

            timeoutId = setTimeout(function () {
                console.log('Shutting down due to idle timeout.');

                // TODO: Should just shut down the HTTP server instead of the whole process.
                process.exit(0);
            }, timeoutSeconds * 1000);
        }

        express.use(function (req, res, next) {
            resetTimeout();
            next();
        });

        resetTimeout();
    }
}

export interface ConnectorSource {
    /**
     * Get all of the datasets as pages of objects.
     *
     * @returns {AsyncPage<any[]>} A page of datasets.
     */
    getJsonDatasets(): AsyncPage<DatasetContainer[]>;

    /**
     * Get a particular dataset given its ID.
     *
     * @param {string} id The ID of the dataset.
     * @returns {Promise<any>} The dataset object with the given ID.
     */
    getJsonDataset(id: string): Promise<any>;

    /**
     * Search datasets for those that have a particular case-insensitive string
     * in their title.
     *
     * @param {string} title The string to search for the in the title.
     * @param {number} maxResults The maximum number of results to return.
     * @returns {AsyncPage<any[]>} A page of matching datasets.
     */
    searchDatasetsByTitle(title: string, maxResults: number): AsyncPage<any[]>;

    /**
     * Gets the distributions of a given dataset.
     *
     * @param {object} dataset The dataset.
     * @returns {AsyncPage<any[]>} A page of distributions of the dataset.
     */
    getJsonDistributions(dataset: any): AsyncPage<any[]>;

    /**
     * True if the source provides organizations as first-class objects that can be enumerated and retrieved
     * by ID.  False if organizations are just fields on datasets or distributions, or if they're not
     * available at all.
     */
    readonly hasFirstClassOrganizations: boolean;

    /**
     * Enumerates first-class organizations.  If {@link hasFirstClassOrganizations} is false, this
     * method returns undefined.
     *
     * @returns {AsyncPage<any[]>} A page of organizations, or undefined if first-class organizations are not available.
     */
    getJsonFirstClassOrganizations(): AsyncPage<any[]>;

    /**
     * Gets a first-class organization by ID. If {@link hasFirstClassOrganizations} is false, this
     * method returns undefined.
     *
     * @param {string} id The ID of the organization to retrieve.
     * @returns {Promise<any>} A promise for the organization, or undefined if first-class organizations are not available.
     */
    getJsonFirstClassOrganization(id: string): Promise<any>;

    /**
     * Search first-class organizations for those that have a particular case-insensitive string
     * in their title.
     *
     * @param {string} title The string to search for the in the title.
     * @param {number} maxResults The maximum number of results to return.
     * @returns {AsyncPage<any[]>} A page of matching organizations, or undefined if first-class organizations are not available.
     */
    searchFirstClassOrganizationsByTitle(title: string, maxResults: number): AsyncPage<any[]>;

    /**
     * Gets the ID of the publisher of this dataset.  This method will return undefined if {@link hasFirstClassOrganizations}
     * is false because non-first-class organizations do not have IDs.
     *
     * @param {any} dataset The dataset from which to get the publisher ID.
     * @returns {string} The ID of the dataset's publisher.
     */
    getJsonDatasetPublisherId(dataset: any): string;

    /**
     * Gets the publisher organization of this dataset.
     *
     * @param {any} dataset The dataset from which to get the publisher.
     * @returns {Promise<object>} A promise for the organization that published this dataset.
     */
    getJsonDatasetPublisher(dataset: any): Promise<any>;
}

export interface JsonConnectorOptions {
    source: ConnectorSource;
    transformer: JsonTransformer;
    registry: Registry;
    maxConcurrency?: number;
}

export interface JsonConnectorRunInteractiveOptions {
    timeoutSeconds: number;
    listenPort: number;
    transformerOptions: any;
}

//retrievedAt is a number because dataset-schema uses json schema that doesn't have a moment as a types as the default. 
export interface DatasetContainer {
    new(dataset: any, retrievedAt: Moment): DatasetContainer
    dataset: any;
    retrievedAt: Moment;
}

export var DatasetContainer: DatasetContainer
