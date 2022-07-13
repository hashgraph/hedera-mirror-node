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

import fs from 'fs';

import path from 'path';

describe('npm dependency check for packaging', () => {
  test('bundledDependencies should match dependencies keys', () => {
    const basePath = path.resolve(process.cwd(), 'package.json');
    const packageJSON = JSON.parse(fs.readFileSync(basePath, 'utf8'));

    expect(packageJSON).not.toBeNull();
    expect(packageJSON.dependencies).not.toBeNull();
    expect(packageJSON.bundledDependencies).not.toBeNull();

    const dependencyKeys = Object.keys(packageJSON.dependencies).sort();
    const dependencyBundleKeys = packageJSON.bundledDependencies.sort();

    expect(dependencyBundleKeys).toStrictEqual(dependencyKeys);
  });
});
