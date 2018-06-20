import React from "react";
import { connect } from "react-redux";
import { Link, Route, Switch, Redirect } from "react-router-dom";
import ProgressBar from "../UI/ProgressBar";
import ReactDocumentTitle from "react-document-title";
import Breadcrumbs from "../UI/Breadcrumbs";
import { bindActionCreators } from "redux";
import {
    fetchDatasetFromRegistry,
    fetchDistributionFromRegistry
} from "../actions/recordActions";
import Tabs from "../UI/Tabs";
import { config } from "../config";
import defined from "../helpers/defined";
import ErrorHandler from "./ErrorHandler";
import RouteNotFound from "./RouteNotFound";
import DatasetDetails from "./Dataset/DatasetDetails";
import DistributionDetails from "./Dataset/DistributionDetails";
import DistributionPreview from "./Dataset/DistributionPreview";
import queryString from "query-string";
import "./RecordHandler.css";
import DatasetSuggestForm from "./Dataset/DatasetSuggestForm";
class RecordHandler extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            addMargin: false
        };
        this.getBreadcrumbs = this.getBreadcrumbs.bind(this);
    }

    componentWillMount() {
        this.props.fetchDataset(
            decodeURIComponent(this.props.match.params.datasetId)
        );
        if (this.props.match.params.distributionId) {
            this.props.fetchDistribution(
                decodeURIComponent(this.props.match.params.distributionId)
            );
        }
    }

    toggleMargin = addMargin => {
        this.setState({ addMargin });
    };

    componentWillReceiveProps(nextProps) {
        if (
            nextProps.match.params.datasetId !==
            this.props.match.params.datasetId
        ) {
            nextProps.fetchDataset(
                decodeURIComponent(nextProps.match.params.datasetId)
            );
        }
        if (
            nextProps.match.params.distributionId &&
            nextProps.match.params.distributionId !==
                this.props.match.params.distributionId
        ) {
            nextProps.fetchDistribution(
                decodeURIComponent(nextProps.match.params.distributionId)
            );
        }
    }

    renderByState() {
        const organisationName = this.props.dataset.organisation.name;
        const searchText =
            queryString.parse(this.props.location.search).q || "";
        const organisationId = this.props.dataset.organisation
            ? this.props.dataset.organisation.id
            : null;

        if (this.props.match.params.distributionId) {
            if (this.props.distributionIsFetching) {
                return <ProgressBar />;
            } else {
                if (this.props.distributionFetchError) {
                    return (
                        <ErrorHandler
                            error={this.props.distributionFetchError}
                        />
                    );
                }
                const tabList = [
                    { id: "details", name: "Details", isActive: true },
                    { id: "preview", name: "Preview", isActive: true }
                ];

                const baseUrlDistribution = `/dataset/${encodeURI(
                    this.props.match.params.datasetId
                )}/distribution/${encodeURI(
                    this.props.match.params.distributionId
                )}`;
                return (
                    <div className="">
                        <h1>{this.props.distribution.title}</h1>
                        <div className="organisation">{organisationName}</div>
                        {defined(this.props.distribution.updatedDate) && (
                            <div className="updated-date">
                                Updated {this.props.distribution.updatedDate}
                            </div>
                        )}

                        <Tabs
                            list={tabList}
                            baseUrl={baseUrlDistribution}
                            params={`q=${searchText}`}
                            onTabChange={tab => {
                                console.log(tab);
                            }}
                        />
                        <div className="tab-content">
                            <Switch>
                                <Route
                                    path="/dataset/:datasetId/distribution/:distributionId/details"
                                    component={DistributionDetails}
                                />
                                <Route
                                    path="/dataset/:datasetId/distribution/:distributionId/preview"
                                    component={DistributionPreview}
                                />
                                <Redirect
                                    from="/dataset/:datasetId/distribution/:distributionId"
                                    to={`${baseUrlDistribution}/details?q=${searchText}`}
                                />
                            </Switch>
                        </div>
                    </div>
                );
            }
        } else if (this.props.match.params.datasetId) {
            if (this.props.datasetIsFetching) {
                return <ProgressBar />;
            } else {
                if (this.props.datasetFetchError) {
                    if (this.props.datasetFetchError.detail === "Not Found") {
                        return (
                            <Redirect
                                to={`/search?notfound=true&q="${encodeURI(
                                    this.props.match.params.datasetId
                                )}"`}
                            />
                        );
                    } else {
                        return (
                            <ErrorHandler
                                error={this.props.datasetFetchError}
                            />
                        );
                    }
                }

                const baseUrlDataset = `/dataset/${encodeURI(
                    this.props.match.params.datasetId
                )}`;

                // redirect old CKAN URL slugs and UUIDs
                if (
                    this.props.dataset.identifier &&
                    this.props.dataset.identifier !== "" &&
                    this.props.dataset.identifier !==
                        this.props.match.params.datasetId
                ) {
                    return (
                        <Redirect
                            to={`/dataset/${encodeURI(
                                this.props.dataset.identifier
                            )}/details?q=${searchText}`}
                        />
                    );
                }
                return (
                    <div itemScope itemType="http://schema.org/Dataset">
                        <div
                            className={
                                this.state.addMargin ? "form-margin" : ""
                            }
                        >
                            <DatasetSuggestForm
                                title={this.props.dataset.title}
                                toggleMargin={this.toggleMargin}
                                datasetId={this.props.dataset.identifier}
                            />
                        </div>
                        <h1 className="dataset-title" itemProp="name">
                            {this.props.dataset.title}
                        </h1>
                        <div className="organisation-basic-info-row">
                            <span
                                itemProp="organisation"
                                itemScope
                                itemType="http://schema.org/Organization"
                            >
                                <Link to={`/organisations/${organisationId}`}>
                                    {organisationName}
                                </Link>
                            </span>
                            <span className="separator hidden-sm"> / </span>
                            {defined(this.props.dataset.issuedDate) && (
                                <span className="updated-date hidden-sm">
                                    Created{" "}
                                    <span itemProp="dateCreated">
                                        {this.props.dataset.issuedDate}
                                    </span>&nbsp;
                                </span>
                            )}
                            <span className="separator hidden-sm">
                                &nbsp;/&nbsp;
                            </span>
                            {defined(this.props.dataset.updatedDate) && (
                                <span className="updated-date hidden-sm">
                                    Updated{" "}
                                    <span itemProp="dateModified">
                                        {this.props.dataset.updatedDate}
                                    </span>
                                </span>
                            )}
                        </div>
                        <div className="tab-content">
                            <Switch>
                                <Route
                                    path="/dataset/:datasetId/details"
                                    component={DatasetDetails}
                                />
                                <Redirect
                                    exact
                                    from="/dataset/:datasetId"
                                    to={`${baseUrlDataset}/details?q=${searchText}`}
                                />
                                <Redirect
                                    exact
                                    from="/dataset/:datasetId/resource/*"
                                    to={`${baseUrlDataset}/details?q=${searchText}`}
                                />
                            </Switch>
                        </div>
                    </div>
                );
            }
        }
        return <RouteNotFound />;
    }

    // build breadcrumbs
    getBreadcrumbs() {
        const params = Object.keys(this.props.match.params);
        const results = (
            <li key="result">
                <Link
                    to={`/search?q=${queryString.parse(
                        this.props.location.search
                    ).q || ""}`}
                >
                    Results
                </Link>
            </li>
        );
        const breadcrumbs = params.map(p => {
            if (p === "datasetId") {
                return (
                    <li key="datasetId">
                        <Link
                            to={`/dataset/${this.props.match.params[p]}${
                                this.props.location.search
                            }`}
                        >
                            {this.props.dataset.title}
                        </Link>
                    </li>
                );
            }

            if (p === "distributionId") {
                return (
                    <li key="distribution">
                        <span>{this.props.distribution.title}</span>
                    </li>
                );
            }

            return null;
        });
        breadcrumbs.unshift(results);
        return breadcrumbs;
    }

    render() {
        const title = this.props.match.params.distributionId
            ? this.props.distribution.title
            : this.props.dataset.title;
        return (
            <ReactDocumentTitle title={title + "|" + config.appName}>
                <div>
                    <Breadcrumbs breadcrumbs={this.getBreadcrumbs()} />
                    {this.renderByState()}
                </div>
            </ReactDocumentTitle>
        );
    }
}

function mapStateToProps(state) {
    const record = state.record;
    const dataset = record.dataset;
    const distribution = record.distribution;
    const datasetIsFetching = record.datasetIsFetching;
    const distributionIsFetching = record.distributionIsFetching;
    const datasetFetchError = record.datasetFetchError;
    const distributionFetchError = record.distributionFetchError;

    return {
        dataset,
        distribution,
        datasetIsFetching,
        distributionIsFetching,
        distributionFetchError,
        datasetFetchError
    };
}

const mapDispatchToProps = dispatch => {
    return bindActionCreators(
        {
            fetchDataset: fetchDatasetFromRegistry,
            fetchDistribution: fetchDistributionFromRegistry
        },
        dispatch
    );
};
export default connect(
    mapStateToProps,
    mapDispatchToProps
)(RecordHandler);
