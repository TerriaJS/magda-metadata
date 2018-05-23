import React from "react";
import AUtextInput from "@gov.au/text-inputs";
import AUbutton from "@gov.au/buttons";
import AUheader from "@gov.au/header";
import "./FormTemplate.css";

//This is the react form template
export default class RequestFormTemplate extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            message: "",
            senderName: "",
            senderEmail: "",
            senderEmailValid: true,
            messageValid: true,
            senderNameValid: true
        };
    }

    /**
     * performs some basic validation on the 3 inputs
     * @param {Object} state current state
     */
    checkRequiredFields(state) {
        //this below package validates emails.
        var validator = require("email-validator");
        const requiredFields = [];
        if (
            !state ||
            !state.senderEmail ||
            state.senderEmail.trim() === "" ||
            !validator.validate(state.senderEmail.trim())
        ) {
            requiredFields.push(`senderEmail`);
            this.setState({ senderEmailValid: false });
        } else {
            this.setState({ senderEmailValid: true });
        }
        if (!state || !state.senderName || state.senderName.trim() === "") {
            requiredFields.push(`senderName`);
            this.setState({ senderNameValid: false });
        } else {
            this.setState({ senderNameValid: true });
        }
        if (!state || !state.message || state.message.trim() === "") {
            requiredFields.push(`message`);
            this.setState({ messageValid: false });
        } else {
            this.setState({ messageValid: true });
        }
        if (requiredFields.length) return requiredFields;
        return null;
    }

    /**
     * If valid this form is submitted.
     */
    handleSubmit = e => {
        e.preventDefault();
        if (this.checkRequiredFields(this.state)) {
            return;
        }
        this.props.handleSubmit(this.state);
    };

    /**
     * Handles change event when typed into any of the form inputs.
     * Sets the state according to which input is being typed in
     */
    handleInputChange = event => {
        const inputId = event.target.id;
        const inputVal = event.target.value.trim();
        this.setState(() => {
            return {
                [inputId]: inputVal
            };
        });
    };

    render() {
        return (
            <form method="post">
                {this.props.title &&
                    (this.props.smallTitle ? (
                        <h3 className="small-title">{this.props.title}</h3>
                    ) : (
                        <AUheader title={this.props.title} />
                    ))}
                <label htmlFor="message" className="field-label">
                    {this.props.textAreaLabel} *
                </label>
                <AUtextInput
                    as="textarea"
                    id="message"
                    className={
                        "textarea-input " +
                        (this.state.messageValid
                            ? ""
                            : "au-text-input--invalid")
                    }
                    onChange={this.handleInputChange}
                    type="text"
                    placeholder={this.props.textAreaPlaceHolder}
                />
                {!this.state.messageValid && (
                    <label className="input-field-error">
                        Please enter a valid message
                    </label>
                )}
                <label htmlFor="senderName" className="field-label">
                    Your Name *
                </label>
                <AUtextInput
                    id="senderName"
                    onChange={this.handleInputChange}
                    type="text"
                    className={
                        "suggest-page-input " +
                        (this.state.senderNameValid
                            ? ""
                            : "au-text-input--invalid")
                    }
                    placeholder={this.props.namePlaceHolder}
                />
                {!this.state.senderNameValid && (
                    <label className="input-field-error">
                        Please enter a name
                    </label>
                )}
                <label htmlFor="senderEmail" className={"field-label"}>
                    Email *
                </label>
                <AUtextInput
                    id="senderEmail"
                    onChange={this.handleInputChange}
                    className={
                        "suggest-page-input " +
                        (this.state.senderEmailValid
                            ? ""
                            : "au-text-input--invalid")
                    }
                    placeholder={this.props.emailPlaceHolder}
                />
                {!this.state.senderEmailValid && (
                    <label className="input-field-error">
                        Email is invalid
                    </label>
                )}
                <AUbutton
                    onClick={this.handleSubmit}
                    className="submit-button"
                    type="submit"
                >
                    {this.props.isSending ? "Sending..." : "Send"}
                </AUbutton>
            </form>
        );
    }
}
