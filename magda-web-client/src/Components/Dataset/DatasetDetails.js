import React, { Component } from "react";
import TemporalAspectViewer from "../../UI/TemporalAspectViewer";
import DatasetPreview from "./DatasetPreview";
import OverviewBox from "../../UI/OverviewBox";
import MarkdownViewer from "../../UI/MarkdownViewer";
import { Link } from "react-router-dom";
import { connect } from "react-redux";
import renderDistribution from "../../Components/Distribution";
import uniq from "lodash.uniq";
import reduce from "lodash.reduce";
import "./RecordDetails.css";

const tagSeperatorRegex = /[,|;|/||]/g;

function mergeTags(tags) {
    return uniq(reduce(tags, (acc, cur) => {
        return acc.concat(cur.split(tagSeperatorRegex).map(item => item.toLowerCase().trim()));
    }, []));
}

class DatasetDetails extends Component {
    state = {
        showPreview: false
    }
    render() {
        const dataset = this.props.dataset;
        const datasetId = this.props.match.params.datasetId;

        const source = `This dataset was originally found on [${this.props.dataset.source}](${dataset.landingPage})`
        return (
            <div className='dataset-details container'>
                <div className='mui-row'>
                    <div className='dataset-details__body mui-col-sm-8'>
                        <div className='dataset-details-overview'>
                            <h3 className='section-heading'>Overview</h3>
                            <OverviewBox content={dataset.description} truncate={false} />
                        </div>
                        <div className='dataset-details-source'>
                            <h3 className='section-heading'>Source</h3>
                            <MarkdownViewer markdown={source} truncate={false} />
                        </div>
                        <div className='dataset-preview'>
                            <DatasetPreview dataset={dataset} />
                        </div>
                        <div className='dataset-details-source'>
                            <h3 className='clearfix'><span className='section-heading'>Data and APIs</span></h3>
                            <div className='clearfix'>{dataset.distributions.map(s => renderDistribution(s, datasetId, this.state.showPreview))}</div>
                        </div>
                        <div className='dataset-details-temporal-coverage'>
                            <h3 className='section-heading'>Temporal coverage</h3>
                            <TemporalAspectViewer data={dataset.temporalCoverage} />
                        </div>
                    </div>

                    <div className="record-details__sidebar mui-col-sm-4">
                        <div className="tags">
                            <h5>Tags</h5>
                            {dataset.tags && mergeTags(dataset.tags).sort((a, b) => {
                                if (a < b) return -1;
                                else if (a > b) return 1;
                                else return 0;
                            }).map(t => <span className="badge" key={t}><Link key={t} to={`/search?q=${encodeURIComponent(t)}`}>{t}</Link></span>)}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}

function mapStateToProps(state) {
    const record = state.record;
    const dataset = record.dataset;
    return {
        dataset
    };
}

export default connect(mapStateToProps)(DatasetDetails);
