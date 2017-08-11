import React from "react";
import { browserHistory } from "react-router";
import fbLogo from "./fb-logo.svg";
import googleLogo from "./google-logo.svg";
import "./Login.css";

import { config } from "../../config";
const { baseUrl } = config;

export default function Login(props) {
  const { pathname: prevPath } =
    browserHistory.lastLocation || browserHistory.getCurrentLocation();

  const baseRedirectUrl = `${window.location.protocol}//${window.location
    .host}`;
  const oauthRedirect = `${baseRedirectUrl}/sign-in-redirect?redirectTo=${prevPath}`;

  const makeLoginUrl = type =>
    `${baseUrl}auth/login/${type}?redirect=${encodeURIComponent(
      oauthRedirect
    )}`;

  return (
    <div className="row login__row">
      {props.signInError &&
        <div className="col-xs-12">
          Sign In Failed: {props.signInError}
        </div>}
      <div className="col-sm-6 col-md-5 col-md-offset-1">
        <h2>Sign In / Register through External Provider</h2>
        <ul className="login__providers">
          {props.providers.includes("facebook") &&
            <li className="login__provider">
              <a href={makeLoginUrl("facebook")}>
                <img src={fbLogo} className="login__logo" alt="logo" />
                Facebook
              </a>
            </li>}
          {props.providers.includes("google") &&
            <li className="login__provider">
              <a href={makeLoginUrl("google")}>
                <img src={googleLogo} className="login__logo" alt="logo" />
                Google
              </a>
            </li>}
        </ul>
      </div>
      {props.providers.includes("ckan") &&
        <div className="col-sm-6 col-md-5">
          <h2>Sign In with Data.gov.au</h2>
          <p>
            This will use your existing data.gov.au account. To register a new
            data.gov.au account,{" "}
            <a href="http://data.gov.au/user/register" target="_blank">
              click here
            </a>.
          </p>
          <form
            action={makeLoginUrl("ckan")}
            method="post"
            className="login__form"
          >
            <div className="login__input-group input-group">
              <div className="input-group-addon">
                <span className="glyphicon glyphicon-user" />
              </div>
              <input
                type="text"
                className="form-control"
                placeholder="Username"
                name="username"
              />
            </div>
            <div className="login__input-group input-group">
              <div className="input-group-addon">
                <span className="glyphicon glyphicon-lock" />
              </div>
              <input
                type="password"
                name="password"
                className="form-control"
                placeholder="Password"
              />
            </div>
            <div className="pull-right">
              <input type="submit" className="btn btn-primary" />
            </div>
          </form>
        </div>}
    </div>
  );
}
