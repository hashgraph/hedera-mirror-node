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

import JSONBigFactory from 'json-bigint';
import {log} from './logger.js';

const JSONBig = JSONBigFactory({useNativeBigInt: true});
const OPTIONS = {compress: true, headers: {'Accept': 'application/json'}};
const PREFIX = '/api/v1';

export class MirrorNodeClient {

  constructor(network) {
    this.network = network;
  }

  _getUrl(path) {
    const prefixedPath = path.startsWith(PREFIX) ? path : PREFIX + path;
    return `https://${this.network}.mirrornode.hedera.com${prefixedPath}`;
  }

  get(path) {
    const url = this._getUrl(path);
    log(`Invoking ${url}`);

    return fetch(url, OPTIONS)
    .then(async res => JSONBig.parse(await res.text()))
    .then(res => {
      if (res._status != null) {
        log(`Error invoking URL: ${JSONBig.stringify(res._status.messages)}`);
        return null;
      }
      return res;
    })
    .catch(e => log(`Error invoking URL: ${e}`));
  }
}
