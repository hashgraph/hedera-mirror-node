/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import OpenApiValidator from 'express-openapi-validator';
import fs from 'fs';
import yaml from 'js-yaml';
import _ from 'lodash';
import path from 'path';
import swaggerUi from 'swagger-ui-express';

// files
import config from '../config';

let v1OpenApiDocument;
let v1OpenApiFile;
let openApiMap;

const pathParameterRegex = /{([^}]*)}/g;
const integerRegexPattern = '\\d{1,10}';

/**
 * Check if apiVersion is currently supported
 * @param {Number} apiVersion
 */
const isInValidVersionRange = (apiVersion) => {
  if (!_.isNumber(apiVersion)) {
    return false;
  }

  // current api version range
  return apiVersion > 0 && apiVersion < 2;
};

/**
 * Get path of open api spec
 * @param {Number} apiVersion
 */
const getSpecPath = (apiVersion) => {
  const apiVersionPath = isInValidVersionRange(apiVersion) ? `v${apiVersion}` : 'v1';
  return `api/${apiVersionPath}/${config.openapi.specFileName}.yml`;
};

/**
 * Get YAML object representing the open api spec
 * @param {Number} apiVersion
 */
const getOpenApiSpecObject = (apiVersion) => {
  return yaml.load(getV1OpenApiFile(apiVersion));
};

/**
 * Get the YAML file of the open api spec for the v1 rest api
 */
const getV1OpenApiFile = (apiVersion) => {
  if (_.isUndefined(v1OpenApiFile)) {
    const openApiSpecPath = path.resolve(process.cwd(), getSpecPath(apiVersion));
    v1OpenApiFile = fs.readFileSync(openApiSpecPath, 'utf8');
  }

  return v1OpenApiFile;
};

/**
 * Get the YAML object of the open api spec for the v1 rest api
 */
const getV1OpenApiObject = () => {
  if (_.isUndefined(v1OpenApiDocument)) {
    v1OpenApiDocument = getOpenApiSpecObject(1);
  }

  return v1OpenApiDocument;
};

/**
 * Get the path to parameter properties map for the OpenApi Spec
 *
 * @returns {Map<string, {parameterName, defaultValue, pattern}>}
 */
const getOpenApiMap = () => {
  if (_.isUndefined(openApiMap)) {
    const openApiObject = getV1OpenApiObject();
    const patternMap = getPathParametersPatterns(openApiObject);
    openApiMap = new Map();
    Object.keys(openApiObject.paths).forEach((path) => {
      const parameters = getOpenApiParameters(path, openApiObject);
      const regex = pathToRegexConverter(path, patternMap);
      openApiMap.set(path, {
        parameters,
        regex,
      });
    });
  }

  return openApiMap;
};

/**
 * Given a path, gets the query parameters and their default values
 * @param path
 * @param openApiObject
 */
const getOpenApiParameters = (path, openApiObject) => {
  const pathObject = openApiObject.paths[path];
  const parameters = pathObject?.get?.parameters;
  if (parameters === undefined) {
    return {};
  }

  return (
    parameters
      // Each open api parameter is prefixed by #/components/parameters/, which is 24 characters long
      .map((p) => p.$ref?.substring(24))
      .filter((p) => p !== undefined)
      .map((p) => openApiObject.components.parameters[p])
      .filter((p) => p.in !== 'path')
      .map((p) => {
        const defaultValue = p.schema?.default;
        const parameterName = p.name;
        return {parameterName, defaultValue};
      })
  );
};

/**
 * Converts an OpenApi path to a regex using the OpenApi regex patterns
 * @param path
 * @param patternMap
 * @returns {RegExp}
 */
const pathToRegexConverter = (path, patternMap) => {
  const splitPath = path.split('/');
  for (let i = 0; i < splitPath.length; i++) {
    const value = splitPath[i];
    if (pathParameterRegex.test(value)) {
      let pattern = patternMap.get(value);
      if (!pattern) {
        // When no pattern is present default to regex for an integer
        pattern = integerRegexPattern;
      } else {
        // Remove beginning and ending of string regex characters
        if (pattern.charAt(0) === '^') {
          pattern = pattern.substring(1);
        }
        if (pattern.charAt(pattern.length - 1) === '$') {
          pattern = pattern.substring(0, pattern.length - 1);
        }
      }

      splitPath[i] = pattern;
    }
  }

  // Add beginning and ending of string regex characters to the entire path
  path = '^' + splitPath.join('/') + '$';
  return new RegExp(path);
};

/**
 * Gets the regex patterns for each of the path parameters
 * @return {Map}
 */
const getPathParametersPatterns = (openApiObject) => {
  const pathParameters = new Map();
  const openApiParameters = openApiObject.components.parameters;
  Object.keys(openApiParameters)
    .map((parameter) => openApiParameters[parameter])
    .filter((parameter) => parameter.in === 'path')
    .forEach((parameter) => {
      // Path parameters are denoted by brackets within the OpenApi paths, such as: /api/v1/accounts/{idOrAliasOrEvmAddress}
      const key = '{' + parameter.name + '}';

      // A schema may be nested within the parameter directly or it may be a reference to a schema in the components/schema object
      // Remove the prefix: #/components/schemas/
      const schemaReference = parameter.schema.$ref?.substring(21);
      const schema = schemaReference ? openApiObject.components.schemas[schemaReference] : parameter.schema;

      const pattern = schema?.pattern;
      pathParameters.set(key, pattern);
    });

  return pathParameters;
};

const serveSpec = (req, res) => res.type('text/yaml').send(getV1OpenApiFile());

/**
 * Serve the open api spec on the given express object
 * @param {ExpressWithAsync} app
 */
const serveSwaggerDocs = (app) => {
  const options = {
    explorer: false,
    customCss:
      '.topbar-wrapper img { content:url(https://camo.githubusercontent.com/cca6b767847bb8ca5c7059481ba13a5fc81c5938/68747470733a2f2f7777772e6865646572612e636f6d2f6c6f676f2d6361706974616c2d686261722d776f72646d61726b2e6a7067); }',
    swaggerOptions: {
      operationsSorter: 'alpha',
      tagsSorter: 'alpha',
    },
  };

  app.get(`/api/v1/${config.openapi.swaggerUIPath}/${config.openapi.specFileName}.yml`, serveSpec);
  app.get(`/api/v1/${config.openapi.swaggerUIPath}/${config.openapi.specFileName}.yaml`, serveSpec);
  app.use(`/api/v1/${config.openapi.swaggerUIPath}`, swaggerUi.serve, swaggerUi.setup(getV1OpenApiObject(), options));
};

const openApiValidator = (app) => {
  app.use(
    OpenApiValidator.middleware({
      apiSpec: path.resolve(process.cwd(), getSpecPath(1)),
      ignoreUndocumented: true,
      validateRequests: false,
      validateResponses: true,
    })
  );
};

export {getV1OpenApiObject, getOpenApiMap, openApiValidator, serveSwaggerDocs};
