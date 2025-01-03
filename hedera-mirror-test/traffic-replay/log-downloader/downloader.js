/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import {Logging} from '@google-cloud/logging';

class Downloader {
  #completed = false;
  #filter;
  #projectId;
  #sinks;

  constructor(filter, projectId, ...sinks) {
    this.#filter = filter;
    this.#projectId = projectId;
    this.#sinks = sinks;
  }

  async download() {
    if (this.#completed) {
      return;
    }

    log(`Downloading logs for project '${this.#projectId}' with filter '${this.#filter}'`);

    const logging = new Logging({projectId: this.#projectId});
    let pageToken = undefined;
    let count = 0;
    let requestIndex = 1;
    const sinks = this.#sinks;

    while (true) {
      const request = {
        filter: this.#filter,
        orderBy: 'timestamp asc',
        pageSize: 100000,
        pageToken,
        resourceNames: [`projects/${this.#projectId}`],
      };
      const response = (await logging.getEntries(request))[2];
      const entries = response.entries;
      pageToken = response.nextPageToken;

      entries.forEach((entry) => sinks.forEach((sink) => sink.accept(entry.textPayload)));

      count += entries.length;
      log(`Request #${requestIndex++} - downloaded ${entries.length} entries`);

      if (!pageToken) {
        break;
      }
    }

    sinks.forEach((sink) => sink.close());
    this.#completed = true;
  }
}

export default Downloader;
