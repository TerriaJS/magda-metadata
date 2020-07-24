import React, { useState } from "react";
import {
    Distribution,
    DistributionSource,
    DatasetStateUpdaterType,
    getDistributionUpdateCallback,
    getDistributionDeleteCallback
} from "Components/Dataset/Add/DatasetAddCommon";
import partial from "lodash/partial";
import "./AddDatasetLinkSection.scss";

import AddDatasetFromLinkInput from "./AddDatasetFromLinkInput";
import DatasetLinkItem from "Components/Dataset/Add/DatasetLinkItem";

type Props = {
    type: DistributionSource.DatasetUrl | DistributionSource.Api;
    distributions: Distribution[];
    datasetStateUpdater: DatasetStateUpdaterType;
    onProcessingComplete?: (distributions: Distribution[]) => void;
};

const AddDatasetLinkSection = (props: Props) => {
    const { type } = props;
    const [processingErrorMessage, setProcessingErrorMessage] = useState("");
    const distributions = props.distributions
        .map((item, idx) => ({ distribution: item, idx }))
        .filter((item) => item.distribution.creationSource === props.type);

    return (
        <div
            className={`row add-dataset-link-section ${
                type === DistributionSource.DatasetUrl
                    ? "source-dataset-url"
                    : "source-api"
            }`}
        >
            <div className="col-sm-12">
                <h2 className="section-heading">
                    {type === DistributionSource.DatasetUrl
                        ? "(and/or) Link to a dataset already hosted online"
                        : "(and/or) Link to an API or web service"}
                </h2>
                {distributions.length ? (
                    <>
                        <div className="row link-items-section">
                            <div className="col-sm-12">
                                {distributions.map((item) => (
                                    <DatasetLinkItem
                                        idx={item.idx}
                                        key={item.idx}
                                        distribution={item.distribution}
                                        onChange={partial(
                                            getDistributionUpdateCallback(
                                                props.datasetStateUpdater
                                            ),
                                            item.distribution.id!
                                        )}
                                        onDelete={partial(
                                            getDistributionDeleteCallback(
                                                props.datasetStateUpdater
                                            ),
                                            item.distribution.id!
                                        )}
                                    />
                                ))}
                            </div>
                        </div>
                        <div className="row link-items-section-heading">
                            <div className="col-sm-12">
                                <h2>
                                    {type === DistributionSource.DatasetUrl
                                        ? "More dataset URL to add?"
                                        : "More web services to add?"}
                                </h2>
                            </div>
                        </div>
                    </>
                ) : null}

                {processingErrorMessage ? (
                    <div className="process-url-error-message au-body au-page-alerts au-page-alerts--warning">
                        <h3>{processingErrorMessage}</h3>
                        <div className="heading">Here’s what you can do:</div>
                        <ul>
                            <li>
                                Double check the URL below is correct and
                                without any typos. If you need to edit the URL,
                                do so below and press ‘Fetch’ again
                            </li>
                            <li>
                                If the URL looks correct, it’s possible we can’t
                                connect to the service or extract any meaningful
                                metadata from it. You may want to try again
                                later
                            </li>
                            <li>
                                If you want to continue using this URL you can,
                                however you’ll need to manually enter the
                                dataset metadata. Use the ‘Manually enter
                                metadata’ button below
                            </li>
                        </ul>
                    </div>
                ) : null}

                <h4 className="url-input-heading">What is the URL?</h4>

                <AddDatasetFromLinkInput
                    type={props.type}
                    datasetStateUpdater={props.datasetStateUpdater}
                    onProcessingError={(e) => {
                        setProcessingErrorMessage(
                            "" + (e.message ? e.message : e)
                        );
                    }}
                    onClearProcessingError={() => setProcessingErrorMessage("")}
                    onProcessingComplete={props.onProcessingComplete}
                />
            </div>
        </div>
    );
};

export default AddDatasetLinkSection;
