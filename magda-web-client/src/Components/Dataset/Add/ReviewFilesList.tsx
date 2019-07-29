import React, { useState, FunctionComponent } from "react";
import { getFormatIcon } from "../View/DistributionIcon";
import { File } from "./DatasetAddCommon";
import iconWhiteArrowLeft from "assets/left-arrow-white.svg";
import iconWhiteArrowUp from "assets/up-arrow-white.svg";

import "./ReviewFilesList.scss";

interface PropsType {
    files: File[];
    isOpen?: boolean;
}

const ReviewFilesList: FunctionComponent<PropsType> = props => {
    const [isOpen, setIsOpen] = useState<boolean>(props.isOpen ? true : false);
    if (!props.files || !Array.isArray(props.files) || !props.files.length)
        return null;
    return (
        <div className="review-files-list-outer-container">
            <div className="review-files-list-container">
                <div className="header-container">
                    <div className="heading">Review files</div>
                    <button
                        aria-label={
                            isOpen
                                ? "Close Review File List"
                                : "Expand Review File List"
                        }
                        className="expand-button"
                        onClick={() => setIsOpen(isOpen ? false : true)}
                    >
                        <img
                            src={isOpen ? iconWhiteArrowLeft : iconWhiteArrowUp}
                        />
                    </button>
                </div>
                {isOpen ? (
                    <div className="body-container">
                        <div className="body-text-container">
                            <p>
                                Magda has reviewed your files and pre-populated
                                metadata fields based on the contents.
                            </p>
                            <p>
                                Please review carefully, and update any fields
                                as required.
                            </p>
                        </div>
                        <div className="file-icons-container">
                            {props.files.map((file, i) => (
                                <div
                                    key={i}
                                    className="file-icon-item clearfix"
                                >
                                    <img
                                        className="file-icon"
                                        src={getFormatIcon(file)}
                                    />
                                    <div className="file-titile">
                                        {file.title}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                ) : null}
            </div>
        </div>
    );
};

export default ReviewFilesList;