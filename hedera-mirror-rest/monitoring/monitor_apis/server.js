/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
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

require('dotenv').config({
  path: './.env'
});

const express = require('express');
const bodyParser = require('body-parser');
const cors = require('cors');
var compression = require('compression');

const common = require('./common.js');
const monitor = require('./monitor.js');

const app = express();

const port = process.env.PORT;
if (port === undefined || isNaN(Number(port))) {
  console.log('Please specify the port');
  process.exit(1);
}

app.set('trust proxy', true);
app.set('port', port);
app.use(
  bodyParser.urlencoded({
    extended: false
  })
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

let apiPrefix = '/api/v1';

common.initResults();

// routes
app.get(apiPrefix + '/status', (req, res) => {
  let status = common.getStatus();
  res.status(status.httpCode).send(status.results);
});
app.get(apiPrefix + '/status/:id', (req, res) => {
  let status = common.getStatusWithId(req.params.id);
  res.status(status.httpCode).send(status);
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    console.log(`Server running on port: ${port}`);
  });
}

// Read the serverlist configuration file, and quit with an error message if the file is invalid.
const serverlist = common.getServerList();
if (!(serverlist.hasOwnProperty('interval') && serverlist.hasOwnProperty('servers') && serverlist.servers.length > 0)) {
  console.log('Error in reading serverlist.json file. Please check the server list and try again.');
  process.exit(1);
}

const runMonitorTests = () => {
  console.log('Running the tests at: ' + new Date());
  monitor.runEverything(serverlist.servers);
};

runMonitorTests();
setInterval(() => {
  // Run all the tests periodically
  runMonitorTests();
}, serverlist.interval * 1000);

module.exports = app;
