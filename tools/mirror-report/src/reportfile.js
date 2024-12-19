/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import fs from "fs";

export class ReportFile {

  constructor() {
    this.count = 0;
    this.data = '';
    this.header = 'timestamp,sender,receiver,fees,amount,balance,hashscan\n';
  }

  _errorHandler(err) {
    if (err) {
      throw err
    }
  };

  append(text) {
    if (this.count === 0) {
      this.data += this.header;
    }

    this.count++;
    this.data += text;
  }

  write(filename) {
    if (this.count > 0) {
      fs.writeFile(filename, this.data, this._errorHandler);
      console.log(
        `${new Date().toISOString()} Generated report successfully at ${filename} with ${this.count} entries`);
    }

    this.count = 0;
    this.data = '';
  }
}
