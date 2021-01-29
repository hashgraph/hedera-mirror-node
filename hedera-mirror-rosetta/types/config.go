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

package types

type Config struct {
    Hedera Hedera `yaml:"hedera"`
}

type Hedera struct {
    Mirror Mirror `yaml:"mirror"`
}

type Mirror struct {
    Rosetta Rosetta `yaml:"rosetta"`
}

type Rosetta struct {
    ApiVersion  string `yaml:"apiVersion" env:"HEDERA_MIRROR_ROSETTA_API_VERSION"`
    Db          Db     `yaml:"db"`
    Network     string `yaml:"network" env:"HEDERA_MIRROR_ROSETTA_NETWORK"`
    NodeVersion string `yaml:"nodeVersion" env:"HEDERA_MIRROR_ROSETTA_NODE_VERSION"`
    Online      bool   `yaml:"online" env:"HEDERA_MIRROR_ROSETTA_ONLINE"`
    Port        string `yaml:"port" env:"HEDERA_MIRROR_ROSETTA_PORT"`
    Realm       string `yaml:"realm" env:"HEDERA_MIRROR_ROSETTA_REALM"`
    Shard       string `yaml:"shard" env:"HEDERA_MIRROR_ROSETTA_SHARD"`
    Version     string `yaml:"version" env:"HEDERA_MIRROR_ROSETTA_VERSION"`
}

type Db struct {
    Host     string `yaml:"host" env:"HEDERA_MIRROR_ROSETTA_DB_HOST"`
    Name     string `yaml:"name" env:"HEDERA_MIRROR_ROSETTA_DB_NAME"`
    Password string `yaml:"password" env:"HEDERA_MIRROR_ROSETTA_DB_PASSWORD"`
    Port     string `yaml:"port" env:"HEDERA_MIRROR_ROSETTA_DB_PORT"`
    Username string `yaml:"username" env:"HEDERA_MIRROR_ROSETTA_DB_USERNAME"`
}
