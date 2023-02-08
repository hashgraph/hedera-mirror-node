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

import bodyParser from 'body-parser';
import compression from 'compression';
import cors from 'cors';
import express from 'express';
import common from './common';
import logger from './logger';
import {runEverything} from './monitor';
import config from './config';

const app = express();

const port = process.env.PORT || config.port || 3000;
if (isNaN(Number(port))) {
  logger.error('Please specify a valid port');
  process.exit(1);
}

app.disable('x-powered-by');
app.set('trust proxy', true);
app.set('port', port);
app.use(
  bodyParser.urlencoded({
    extended: false,
  })
);
app.use(bodyParser.json());
app.use(compression());
app.use(cors());

const apiPrefix = '/api/v1';
const healthDown = '{"status": "DOWN"}';
const healthUp = '{"status": "UP"}';

common.initResults();

// routes
app.get('/health/liveness', (req, res) => res.status(200).send(healthUp));
app.get('/health/readiness', (req, res) => {
  const status = common.getStatus();
  const total = status.results.map((r) => r.results.testResults ? 1 : 0).reduce((r, i) => r + i);

  if (total > 0) {
    res.status(200).send(healthUp);
  } else {
    res.status(503).send(healthDown);
  }
});

app.get(`${apiPrefix}/status`, (req, res) => {
  const status = common.getStatus();
  const passed = status.results.map((r) => r.results.numPassedTests).reduce((r, i) => r + i);
  const total = status.results.map((r) => r.results.testResults.length).reduce((r, i) => r + i);
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).send(status.results);
});

app.get(`${apiPrefix}/status/:name`, (req, res) => {
  const status = common.getStatusByName(req.params.name);
  const passed = status.results.numPassedTests;
  const total = status.results.testResults.length;
  logger.info(
    `${req.ip} ${req.method} ${req.originalUrl} returned ${status.httpCode}: ${passed}/${total} tests passed`
  );
  res.status(status.httpCode).send(status);
});

if (process.env.NODE_ENV !== 'test') {
  app.listen(port, () => {
    logger.info(`Server running on port: ${port}`);
  });
}

const servers = [...config.servers].map((server) => {
  return {
    ...server,
    running: false,
  };
});

// Run tests on startup and every interval after that
runEverything(servers).then(() => {
  setInterval(async () => {
    await runEverything(servers);
  }, config.interval * 1000);
});

export default app;
