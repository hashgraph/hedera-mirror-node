/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
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

let monitorAddress = 'localhost:3000';

const loadConfig = async () => {
  return fetch('/config.json', {mode: 'no-cors'})
    .then((response) => response.json())
    .then((config) => {
      if (config.monitorAddress) {
        console.log('Loaded config.json');
        monitorAddress = config.monitorAddress;
      }
    })
    .catch((error) => console.log(`Unable to load config.json. Using default config: ${error}`));
};

/**
 * Creates an html table from the test results
 * @param {Object} data The data returned by the REST API
 * @param {Object} server The server under test
 * @return {HTML} HTML for the table
 */
const makeTable = (data) => {
  const results = data.results.testResults;
  if (results === undefined) {
    return 'No result yet to display';
  }

  let h = `
        <table class="table table-sm table-hover">
          <thead>
            <tr>
              <th scope="col">Result</th>
              <th scope="col">Time</th>
              <th scope="col">Resource</th>
              <th scope="col">Message</th>
            </tr>
          </thead>
          <tbody`;

  results.sort((a, b) => a.resource.localeCompare(b.resource) || a.url.localeCompare(b.url));
  results.forEach((result) => {
    // Skip the 'Skipped tests' that are marked as pending in jest json output
    if (result.result === 'pending') {
      return;
    }
    const failureMsg =
      result.failureMessages === undefined ? '' : result.failureMessages.join('<br>,').replaceAll('\n', '<br>');
    const status = result.result === 'passed' ? 'success' : 'failure';

    h += `
      <tr>
        <td class="centered"><span class="dot ${status}"></span></td>
        <td class="date">${new Date(Number(result.at)).toISOString()}</td>
        <td>${result.resource}</td>
        <td><a = href="${result.url}">${result.message}${failureMsg}<a/></td>
      </tr>`;
  });
  h += '</tbody></table>\n';
  return h;
};

/**
 * Makes a card for the given server
 * @param {Object} data The data returned by the REST API
 * @param {Object} server The server under test
 * @return {HTML} HTML for the card for the given server
 */
const makeCard = (data) => {
  if (!('results' in data)) {
    return 'No data received yet for at least one of the servers in the list';
  }

  const server = data.name;
  const status = data.results.success ? 'success' : 'failure';

  return `
        <div class="card rounded-lg">
          <div class="${status}" id="result-${server}">
            <button class="btn btn-link btn-block text-left" type="button" data-toggle="collapse"
                      data-target="#collapse-${server}" aria-expanded="false" aria-controls="#collapse-${server}">
              <div class="container card-text">
                <div class="row">
                  <div class="col">
                    <h5 class="mb-0">${server}</h5 class="mb-0">
                    <span>${data.baseUrl}</span>
                  </div>
                  <div id="numTests" class="col">
                    <span>${data.results.numPassedTests} / ${data.results.testResults.length}</span>
                  </div>
                </div>
              </div>
            </button>
          </div>
          <div class="collapse" id="collapse-${server}" aria-labelledby="result-${server}" data-parent="#results">
            <div class="card-body">
              ${makeTable(data)}
            </div>
          </div>
        </div>`;
};

const alertBanner = (url, error) => {
  console.log(`Error: ${error}`);
  return `<div class="alert alert-danger alert-dismissible fade show" role="alert">
            <span>Error fetching <a href="${url}">${url}</a>: ${error}</span>
            <button type="button" class="close" data-dismiss="alert" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
          </div>`;
};

/**
 * Fetch the results from the backend and display the results
 * @param {} None
 * @return {} None
 */
const init = async () => {
  const container = document.getElementById('root');
  if (container === null) {
    console.log('No container found!');
    return;
  }

  await loadConfig();
  const url = `http://${monitorAddress}/api/v1/status`;
  console.log(`Fetching ${url}`);
  let html = `<h2 class="centered">Hedera Mirror Node REST API Monitor</h2>`;

  await fetch(url)
    .then((response) => {
      return response.json();
    })
    .then((data) => {
      console.log(data);
      if (data.length === 0) {
        const message = `No data received. Please wait a few minutes and refresh the page.`;
        html += alertBanner(url, message);
      } else {
        html += `<div class="accordion" id="results">${data.map((result) => makeCard(result)).join('')}</div>`;
      }
    })
    .catch((e) => {
      html += alertBanner(url, e);
    });

  container.innerHTML = html;
};
