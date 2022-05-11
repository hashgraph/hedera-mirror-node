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

const utils = require('../utils');

/**
 * Block view model
 */
class BlockViewModel {
  /**
   * Constructs block view model
   *
   * @param {Block} recordFile
   */
  constructor(recordFile) {
    this.count = BigInt(recordFile.count);
    this.hapi_version = recordFile.getFullHapiVersion();
    this.hash = utils.addHexPrefix(recordFile.hash);
    this.name = recordFile.name;
    this.number = BigInt(recordFile.index);
    this.previous_hash = utils.addHexPrefix(recordFile.prevHash);
    this.size = recordFile.bytes ? recordFile.bytes.length : null;
    this.timestamp = {
      from: utils.nsToSecNs(recordFile.consensusStart),
      to: utils.nsToSecNs(recordFile.consensusEnd),
    };
  }
}

module.exports = BlockViewModel;
