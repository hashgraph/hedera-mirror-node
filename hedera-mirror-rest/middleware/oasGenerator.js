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
const {handleRequests, handleResponses, init} = require('express-oas-generator');
const swaggerUi = require('swagger-ui-express');
const _ = require('lodash');

const oasDocumentV2 = require('./../oas_doc.json');
const oasDocumentV3 = require('./../oas_doc_v3.json');
let urlPrefix;

const oasGeneratorInit = (app, apiPrefix) => {
  handleOASResponses(app);
  // init(app, function (spec) {
  //     _.set(spec, 'info.contact', {
  //       name: 'Hedera Mirror Node Team 2',
  //       email: 'mirrornode@hedera.com',
  //       url: 'https://github.com/hashgraph/hedera-mirror-node',
  //     });
  //     _.set(spec, 'info.license.url', 'https://www.apache.org/licenses/LICENSE-2.0.html');
  //     // _.set(
  //     //   spec,
  //     //   'info.description',
  //     //   `Specification JSONs: [v2](${urlPrefix}/api-spec/v2), [v3](${urlPrefix}/api-spec/v3).\n\nHedera Mirror Node REST API 3`
  //     // );
  //     _.set(spec, `paths["${urlPrefix}/transactions/{id}"].get.parameters[0].description`, 'Transaction id details');
  //     return spec;
  //   },
  //   './oas_doc.json',
  //     1000,
  //     `${urlPrefix}/api-spec`,
  //     null,
  //     ['accounts', 'balances', 'transactions', 'topics', 'tokens'],
  //     ['production']);

  // urlPrefix = apiPrefix;
  // app.use(`${urlPrefix}/api-spec`, swaggerUi.serve, swaggerUi.setup(oasDocumentV2));
  // app.use(`${urlPrefix}/api-spec/v2`, swaggerUi.serve, swaggerUi.setup(oasDocumentV2));
  // app.use(`${urlPrefix}/api-spec/v3`, swaggerUi.serve, swaggerUi.setup(oasDocumentV3));
};

const handleOASRequests = (app, urlPrefix) => {
  handleRequests();

  // serveOASSwaggerUI(app, urlPrefix);
};

const handleOASResponses = (app, urlPrefix) => {
  handleResponses(app, {
    predefinedSpec: function (spec) {
      _.set(spec, 'info.contact', {
        name: 'Hedera Mirror Node Team',
        email: 'mirrornode@hedera.com',
        url: 'https://github.com/hashgraph/hedera-mirror-node',
      });
      _.set(spec, 'info.license.url', 'https://www.apache.org/licenses/LICENSE-2.0.html');
      // _.set(
      //   spec,
      //   'info.description',
      //   `Specification JSONs: [v2](${urlPrefix}/api-spec/v2), [v3](${urlPrefix}/api-spec/v3).\n\nHedera Mirror Node REST API`
      // );
      _.set(spec, `paths["${urlPrefix}/transactions/{id}"].get.parameters[0].description`, 'Transaction id details');
      return spec;
    },
    specOutputPath: './oas_doc.json',
    writeIntervalMs: 0,
    predefinedSpec: undefined,
    // writeIntervalMs: 60 * 1000,
    swaggerUiServePath: `${urlPrefix}/api-spec`,
    mongooseModels: null,
    tags: ['accounts', 'balances', 'transactions', 'topics', 'tokens'],
    // ignoredNodeEnvironments: ['production'],
    alwaysServeDocs: true,
  });

  serveOASSwaggerUI(app, urlPrefix);
};

const serveOASSwaggerUI = (app, apiPrefix) => {
  urlPrefix = apiPrefix;
  app.use(`${urlPrefix}/api-spec`, swaggerUi.serve, swaggerUi.setup(oasDocumentV2));
  app.use(`${urlPrefix}/api-spec/v2`, swaggerUi.serve, swaggerUi.setup(oasDocumentV2));
  app.use(`${urlPrefix}/api-spec/v3`, swaggerUi.serve, swaggerUi.setup(oasDocumentV3));
};

module.exports = {
  handleOASResponses,
  handleOASRequests,
  oasGeneratorInit,
  serveOASSwaggerUI,
};
