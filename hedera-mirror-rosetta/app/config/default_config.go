/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

package config

const defaultConfig = `
hedera:
  mirror:
    rosetta:
      apiVersion: 1.4.10
      db:
        host: 127.0.0.1
        name: mirror_node
        password: mirror_rosetta_pass
        pool:
          maxIdleConnections: 20
          maxLifetime: 30
          maxOpenConnections: 100
        port: 5432
        statementTimeout: 20
        username: mirror_rosetta
      log:
        level: info
      network: DEMO
      nodes:
      nodeVersion: 0
      online: true
      port: 5700
      realm: 0
      shard: 0
      version: 0.42.0-SNAPSHOT
`
