/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
'uses strict';

// recordFile object. Read buffer, parse file, potentially stores all transactionConsensusNs as keys, hash
// file_hash, consensus_start, consensus_end
// parse rcd file looking for transaction with matching consensus time stamp from transaction id

// external libraries

class recordFile {
  constructor(recordFileString) {
    console.log(`Parsing record file`);
    this.hash = 'hash';
    this.consensusStart = 'consensus_start';
    this.consensusEnd = 'consensus_end';
  }
}

module.exports = {
  recordFile,
};
