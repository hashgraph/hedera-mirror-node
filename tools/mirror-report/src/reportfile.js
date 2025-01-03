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

import fs from 'fs';
import {log} from './logger.js';

export class ReportFile {

  static HEADER = 'timestamp,sender,receiver,fees,amount,balance,hashscan\n';

  constructor() {
    this.data = [];
  }

  append(text) {
    this.data.push(text);
  }

  write(filename) {
    if (this.data.length > 0) {
      const text = ReportFile.HEADER + this.data.sort().join('');
      fs.writeFileSync(filename, text);
      log(`Generated report successfully at ${filename} with ${this.data.length} entries`);
      this.data = [];
    }
  }
}
