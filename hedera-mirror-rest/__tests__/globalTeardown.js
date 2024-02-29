/*
 * Copyright (C) 2019-2024 Hedera Hashgraph, LLC
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
import log4js from 'log4js';
import path from 'path';
import {getContainerRuntimeClient} from 'testcontainers';

export default async function () {
  const containerIdFile = path.join(process.env.CONTAINER_TEMP_DIR, 'tempContainerIds');
  if (containerIdFile) {
    const client = await getContainerRuntimeClient();
    const fileContent = fs.readFileSync(containerIdFile, 'utf-8');
    const containerIds = new Set(fileContent.split('\n').filter(Boolean));
    for (const id of containerIds) {
      const container = client.container.getById(id);
      await client.container.stop(container);
      await client.container.remove(container);
    }

    fs.rmSync(process.env.CONTAINER_TEMP_DIR, {force: true, recursive: true});
    console.info(`Removed temp directory ${process.env.CONTAINER_TEMP_DIR}`);
  }
  log4js.shutdown();
}
