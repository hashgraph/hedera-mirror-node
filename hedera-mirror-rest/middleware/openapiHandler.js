/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

export {getV1OpenApiObject, openApiValidator, serveSwaggerDocs};
