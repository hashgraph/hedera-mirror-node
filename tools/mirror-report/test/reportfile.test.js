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
import os from 'os';
import path from 'path';
import {ReportFile} from '../src/reportfile.js';

let outputFile;
let reportFile;

beforeEach(() => {
  const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'mirror-report-'));
  outputFile = path.join(tempDir, 'report.csv');
  reportFile = new ReportFile();
});

afterEach(() => {
  fs.rmdirSync(path.dirname(outputFile), {force: true, recursive: true});
});

describe('ReportFile', () => {
  test('No data', async () => {
    reportFile.write(outputFile);
    expect(fs.existsSync(outputFile)).toBeFalsy();
  });

  test('Has data', async () => {
    const line = 'line1\n';
    reportFile.append(line)
    reportFile.write(outputFile);
    const data = fs.readFileSync(outputFile, 'utf8');
    expect(data).toBe(ReportFile.HEADER + line);
  });
});
