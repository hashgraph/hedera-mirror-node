/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

'use strict';

// ext libraries
const {handleRequests, handleResponses} = require('express-oas-generator');
const fs = require('fs');
const path = require('path');
const swaggerUi = require('swagger-ui-express');
const _ = require('lodash');

// files
const config = require('./../config');
let oasDocumentV2 = undefined;
let oasDocumentV3 = undefined;

/**
 * Get path of open api spec of provided version
 * @param {Number} version
 */
const getSpecPath = (version) => {
  // default to open api spec 3
  const versionPath = version === 2 ? '' : '_v3';
  return `${config.oasGenerator.specFilePath}${versionPath}.json`;
};

/**
 * Get the JSON object of the v2 open api spec
 */
const getOpenApiV2Object = () => {
  if (_.isUndefined(oasDocumentV2)) {
    oasDocumentV2 = getOpenApiSpecObject(2);
  }

  return oasDocumentV2;
};

/**
 * Get the JSON object of the v2 open api spec
 */
const getOpenApiV3Object = () => {
  if (_.isUndefined(oasDocumentV3)) {
    oasDocumentV3 = getOpenApiSpecObject(3);
  }

  return oasDocumentV3;
};

/**
 * Get JSON object representing the open api spec of provided version
 * @param {Number} version
 */
const getOpenApiSpecObject = (version) => {
  const basePath = path.resolve(process.cwd(), getSpecPath(version));
  return JSON.parse(fs.readFileSync(basePath, 'utf8'));
};

/**
 * Serve the open api spec on the given express object using the given apiPrefix path
 * @param {ExpressWithAsync} app
 * @param {String} apiPrefix
 */
const serveOASSwaggerUI = (app, apiPrefix) => {
  // default spec endpoint
  app.use(`${apiPrefix}/${config.oasGenerator.swaggerUIPath}`, swaggerUi.serve, swaggerUi.setup(getOpenApiV3Object()));
  // support explicit v2 enpoint added by express-oas-generator
  app.use(
    `${apiPrefix}/${config.oasGenerator.swaggerUIPath}/v2`,
    swaggerUi.serve,
    swaggerUi.setup(getOpenApiV2Object())
  );
  // support explicit v3 enpoint added by express-oas-generator
  app.use(
    `${apiPrefix}/${config.oasGenerator.swaggerUIPath}/v3`,
    swaggerUi.serve,
    swaggerUi.setup(getOpenApiV3Object())
  );
};

/**
 * Return JSOn object specifying predefined open api spec values
 * @param {JSON} spec
 * @param {String} urlPrefix
 */
const getPredefinedSpec = (spec, urlPrefix) => {
  // set contact info
  _.set(spec, 'info.contact', {
    name: 'Hedera Mirror Node Team',
    email: 'mirrornode@hedera.com',
    url: 'https://github.com/hashgraph/hedera-mirror-node',
  });
  // set license info
  _.set(spec, 'info.license.url', 'https://www.apache.org/licenses/LICENSE-2.0.html');
  // set endpoint paramater descriptions
  _.set(spec, `paths["${urlPrefix}/accounts/{id}"].get.parameters[0].description`, 'Account entity id');
  _.set(spec, `paths["${urlPrefix}/transactions/{id}"].get.parameters[0].description`, 'Transaction id');
  _.set(spec, `paths["${urlPrefix}/topics/{id}/messages"].get.parameters[0].description`, 'Topic entity id');
  _.set(
    spec,
    `paths["${urlPrefix}/topics/{id}/messages/{sequencenumber}"].get.parameters[0].description`,
    'Topic entity id'
  );
  _.set(
    spec,
    `paths["${urlPrefix}/topics/{id}/messages/{sequencenumber}"].get.parameters[1].description`,
    'Topic message sequence number'
  );
  _.set(
    spec,
    `paths["${urlPrefix}/topics/messages/{consensusTimestamp}"].get.parameters[0].description`,
    'Consensus timestamp of topic message'
  );
  _.set(spec, `paths["${urlPrefix}/tokens/{id}"].get.parameters[0].description`, 'Token entity id');
  _.set(spec, `paths["${urlPrefix}/tokens/{id}/balances"].get.parameters[0].description`, 'Token entity id');
  // set external Doc
  _.set(spec, 'externalDocs', {
    description: 'Hedera REST API Docs',
    url: 'https://docs.hedera.com/guides/docs/mirror-node-api/cryptocurrency-api',
  });
  return spec;
};

/**
 * Call the express-oas-generator handleRequests.
 * This must be called after al express middlerwares
 */
const handleOASRequests = () => {
  handleRequests();
};

/**
 * Call the express-oas-generator handleResponses and serve the generated open api specs
 * This must be the first express middle ware called to ensure all responses and url query params are caught
 * @param {ExpressWithAsync} app
 * @param {String} urlPrefix
 */
const handleOASResponses = (app, urlPrefix) => {
  handleResponses(app, {
    alwaysServeDocs: config.oasGenerator.alwaysServeDocs,
    ignoredNodeEnvironments: ['production'],
    mongooseModels: null,
    predefinedSpec: (spec) => getPredefinedSpec(spec, urlPrefix),
    specOutputPath: config.oasGenerator.enabled ? `${config.oasGenerator.specFilePath}.json` : undefined,
    swaggerUiServePath: `${urlPrefix}/${config.oasGenerator.swaggerUIPath}`,
    tags: ['accounts', 'balances', 'transactions', 'topics', 'tokens'],
    writeIntervalMs: config.oasGenerator.writeIntervalMs,
  });

  // configure swagger ui doc serve
  serveOASSwaggerUI(app, urlPrefix);
};

module.exports = {
  getOpenApiV3Object,
  handleOASResponses,
  handleOASRequests,
};
