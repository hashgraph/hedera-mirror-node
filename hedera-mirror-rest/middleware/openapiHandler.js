/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

'use strict';

// ext libraries
const fs = require('fs');
const path = require('path');
const swaggerUi = require('swagger-ui-express');
const _ = require('lodash');
const yaml = require('js-yaml');

// files
const config = require('./../config');
let v1OpenApiDocument = undefined;

/**
 * Check if apiVersion is currently supported
 * @param {Number} version
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
 * @param {Number} version
 */
const getSpecPath = (apiVersion) => {
  const apiVersionPath = isInValidVersionRange(apiVersion) ? `v${apiVersion}` : 'v1';
  return `api/${apiVersionPath}/${config.openapi.specFileName}.yml`;
};

/**
 * Get YAML object representing the open api spec
 * @param {Number} version
 */
const getOpenApiSpecObject = (apiVersion) => {
  const openApiSpecPath = path.resolve(process.cwd(), getSpecPath(apiVersion));
  return yaml.load(fs.readFileSync(openApiSpecPath, 'utf8'));
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
 * Serve the open api spec on the given express object
 * @param {ExpressWithAsync} app
 */
const serveSwaggerDocs = (app) => {
  var options = {
    explorer: true,
    customCss: '.topbar-wrapper img { content:url(https://s3.amazonaws.com/hedera-com/hedera_hashgraph.png); }',
  };
  app.use(`/api/v1/${config.openapi.swaggerUIPath}`, swaggerUi.serve, swaggerUi.setup(getV1OpenApiObject(), options));
};

module.exports = {
  getV1OpenApiObject,
  serveSwaggerDocs,
};
