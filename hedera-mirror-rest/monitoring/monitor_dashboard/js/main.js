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

// TODO: Make this configurable
// This needs to be configured before deploying this file
let config = {
  server: 'localhost:3000'
};

/**
 * Initializer - fetches the status of tests from the server
 * @param {} None
 * @return {} None
 */
const init = () => {
  let app = document.getElementById('root');

  app.innerHTML = '';

  let container = document.createElement('div');
  container.setAttribute('class', 'container');
  container.id = 'rootcontainer';
  app.appendChild(container);

  fetchAndDisplay(container);
};

/**
 * Creates an html table from the test results
 * @param {Object} data The data returned by the REST API
 * @param {Object} server The server under test
 * @return {HTML} HTML for the table
 */
const makeTable = (data, server) => {
  if (data.results.testResults === undefined) {
    return 'No result yet to display';
  }

  let h = '';
  h += `
        <table border="1px">
            <tr>
                <th>Result</th>
                <th>At</th>
                <th>Message & Url</th>
        </tr>`;
  data.results.testResults.forEach(result => {
    // Skip the 'Skipped tests' that are marked as pending in jest json output
    if (result.result === 'pending') {
      return;
    }
    const failureMsg =
      result.failureMessages == undefined ? '' : result.failureMessages.join('<br>,').replace('\n', '<br>');

    h +=
      '<tr>' +
      '<tr>' +
      '<tr>' +
      '<td>' +
      '<span class="dot" style="background-color:' +
      (result.result === 'passed' ? 'green' : 'red') +
      '"></span>' +
      '</td>' +
      '<td>' +
      new Date(Number(result.at)).toLocaleString() +
      '</td>' +
      '<td>' +
      '<a = href="' +
      result.url +
      '">' +
      result.message +
      failureMsg +
      '<a/></td>' +
      '</tr>\n';
  });
  h += '</table>\n';
  return h;
};

/**
 * Makes a card for the given server
 * @param {Object} data The data returned by the REST API
 * @param {Object} server The server under test
 * @return {HTML} HTML for the card for the given server
 */
const makeCard = (data, server) => {
  if (!('results' in data)) {
    return 'No data received yet for at least one of the servers in your list ...';
  }

  const cntTotal = data.results.numPassedTests + data.results.numFailedTests;
  const dotcolor = data.results.success ? 'green' : 'red';
  const startTime = data.results.startTime ? data.results.startTime : 0;
  const endTime = data.results.endTime ? data.results.endTime : 0;

  let h = '';
  // Create a summary card
  h += `
        <div class="card my-card">
          <div class="card-body" data-toggle="modal" data-target="#modal-${server}">
            <div class="card-title">Network: ${server}, ${new Date(Number(startTime)).toISOString()} - ${new Date(
    Number(endTime)
  ).toISOString()}
            </div>
            <div class="ip-addr"> (${data.ip}:${data.port})</div>
            <div class="card-text">
               <div style="display: inline-block; verticle-align: bottom">
            <span class="dot" style="background-color: ${dotcolor}"></span>
               ${data.results.numPassedTests} / ${cntTotal} Passed,
               ${data.results.numFailedTests} / ${cntTotal} Failed.&nbsp;&nbsp;&nbsp;
               </div>
               <div class="card-arrow">&#x25B6</div>
            </div>
          </div>
        </div>`;

  // Create a modal for showing test details
  h += `
        <div class="modal fade" id="modal-${server}">
          <div class="modal-dialog modal-lg">
            <div class="modal-content">
              <div class="modal-header">
                <h4 class="modal-title">Network: ${server}</h4>
                  <button type="button" class="close" data-dismiss="modal">&times;</button>
              </div>
              <div class="modal-body">
                  ${makeTable(data, server)}
              </div>
              <div class="modal-footer">
              </div>
            </div>
          </div>
        </div>`;

  return h;
};

/**
 * Fetch the results from the backend and display the results
 * @param {} None
 * @return {} None
 */
const fetchAndDisplay = () => {
  const container = document.getElementById('rootcontainer');
  if (container === null) {
    console.log('No container found!');
    return;
  }

  fetch(`http://${config.server}/api/v1/status`)
    .then(function(response) {
      return response.json();
    })
    .then(function(data) {
      console.log(data);
      let html;
      if (data.length === 0) {
        html = `No data received.
            <p />
            If you have started the backend server recently,
            please wait for a couple of minutes and refresh this page
            <p />`;
      } else {
        html = `
                <h2 style="text-align:center">Hedera mirror node status</h2>
                ${Object.keys(data)
                  .map(server => `<div class="card-deck">${makeCard(data, server)}</div>`)
                  .join('')}
            `;

        html = `
                <h2 style="text-align:center">Hedera mirror node status</h2>
                ${data.map(result => `<div class="card-deck">${makeCard(result, result.name)}</div>`).join('')}
            `;
      }
      container.innerHTML = html;
    });
};
