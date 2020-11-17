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
const swaggerUi = require('swagger-ui-express');
const _ = require('lodash');

// files
const config = require('./../config');

const getSpecPath = (version) => {
  const versionPath = version === 3 ? '_v3' : '';
  return `${config.oasGenerator.specFileName}${versionPath}.json`;
};

const oasDocumentV2 = require(`./../${getSpecPath(2)}`);
const oasDocumentV3 = require(`./../${getSpecPath(3)}`);

const handleOASRequests = () => {
  handleRequests();
};

const handleOASResponses = (app, urlPrefix) => {
  handleResponses(app, {
    alwaysServeDocs: config.oasGenerator.alwaysServeDocs,
    ignoredNodeEnvironments: ['production'],
    mongooseModels: null,
    predefinedSpec: function (spec) {
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
      return spec;
    },
    specOutputPath: config.oasGenerator.enabled ? `${config.oasGenerator.specFileName}.json` : undefined,
    swaggerUiServePath: `${urlPrefix}/api-spec`,
    tags: ['accounts', 'balances', 'transactions', 'topics', 'tokens'],
    writeIntervalMs: config.oasGenerator.writeIntervalMs,
  });

  // configure swaagger ui doc serve
  serveOASSwaggerUI(app, urlPrefix);
};

const serveOASSwaggerUI = (app, apiPrefix) => {
  app.use(`${apiPrefix}/api-spec`, swaggerUi.serve, swaggerUi.setup(oasDocumentV3));
  app.use(`${apiPrefix}/api-spec/v2`, swaggerUi.serve, swaggerUi.setup(oasDocumentV2));
  app.use(`${apiPrefix}/api-spec/v3`, swaggerUi.serve, swaggerUi.setup(oasDocumentV3));
};

module.exports = {
  getSpecPath,
  handleOASResponses,
  handleOASRequests,
  serveOASSwaggerUI,
};
