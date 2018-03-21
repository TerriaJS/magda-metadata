import React, { Component } from "react";
import { connect } from "react-redux";
import { config } from "../../config";
import { bindActionCreators } from "redux";
import { fetchPublishersIfNeeded } from "../../actions/publisherActions";
import ReactDocumentTitle from "react-document-title";
import PublisherSummary from "./PublisherSummary";
import Pagination from "../../UI/Pagination";
import ErrorHandler from "../../Components/ErrorHandler";
import getPageNumber from "../../helpers/getPageNumber";
import ProgressBar from "../../UI/ProgressBar";
import queryString from "query-string";
import PropTypes from "prop-types";

import "./PublishersViewer.css";
class PublishersViewer extends Component {
    componentWillMount() {
        this.props.fetchPublishersIfNeeded(getPageNumber(this.props) || 1);
    }

    componentWillReceiveProps(nextProps) {
        if (getPageNumber(this.props) !== getPageNumber(nextProps)) {
            nextProps.fetchPublishersIfNeeded(getPageNumber(nextProps) || 1);
        }
    }

    onPageChange(i) {
        this.context.router.history.push({
            pathname: this.props.location.pathname,
            search: queryString.stringify(
                Object.assign(queryString.parse(this.props.location.search), {
                    page: i
                })
            )
        });
    }

    renderContent() {
        if (this.props.error) {
            return <ErrorHandler error={this.props.error} />;
        } else {
            return (
                <div className="col-sm-8">
                    {this.props.publishers.map(p => (
                        <PublisherSummary publisher={p} key={p.id} />
                    ))}
                    {this.props.hitCount > config.resultsPerPage && (
                        <Pagination
                            currentPage={+getPageNumber(this.props) || 1}
                            maxPage={Math.ceil(
                                this.props.hitCount / config.resultsPerPage
                            )}
                            onPageChange={this.onPageChange.bind(this)}
                            totalItems={this.props.hitCount}
                        />
                    )}
                </div>
            );
        }
    }

    render() {
        return (
            <ReactDocumentTitle title={"Publishers | " + config.appName}>
                <div className="container publishers-viewer">
                    <div className="row">
                        {!this.props.isFetching && this.renderContent()}
                        {this.props.isFetching && <ProgressBar />}
                    </div>
                </div>
            </ReactDocumentTitle>
        );
    }
}

function mapDispatchToProps(dispatch: Function) {
    return bindActionCreators(
        {
            fetchPublishersIfNeeded: fetchPublishersIfNeeded
        },
        dispatch
    );
}

function mapStateToProps(state, ownProps) {
    const publishers: Array<Object> = state.publisher.publishers;
    const isFetching: boolean = state.publisher.isFetchingPublishers;
    const hitCount: number = state.publisher.hitCount;
    const error: Object = state.publisher.errorFetchingPublishers;
    const location: Location = ownProps.location;
    return {
        publishers,
        isFetching,
        hitCount,
        location,
        error
    };
}

PublishersViewer.contextTypes = {
    router: PropTypes.object.isRequired
};

export default connect(mapStateToProps, mapDispatchToProps)(PublishersViewer);
