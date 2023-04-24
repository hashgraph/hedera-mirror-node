/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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
import path from 'path';

import {getModuleDirname} from '../testutils';

const mark = '$$GROUP_SPEC_PATH$$';

const dirname = getModuleDirname(import.meta);
const specsPath = path.join(dirname, '..', 'specs');
const template = fs.readFileSync(path.join(dirname, 'template.js')).toString();

fs.readdirSync(specsPath, {withFileTypes: true})
  .filter((dirent) => dirent.isDirectory())
  .forEach((dirent) => {
    const group = dirent.name;
    const filename = path.join(dirname, `${group}.test.js`);
    fs.writeFileSync(filename, template.replace(mark, `'${group}'`));
  });
