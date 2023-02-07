/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

package domain

const tableNameRecordFile = "record_file"

type RecordFile struct {
	ConsensusStart   int64
	ConsensusEnd     int64 `gorm:"primaryKey"`
	Count            int64
	DigestAlgorithm  int
	FileHash         string
	HapiVersionMajor int
	HapiVersionMinor int
	HapiVersionPatch int
	Hash             string
	Index            int64
	LoadEnd          int64
	LoadStart        int64
	Name             string
	NodeId           int64
	PrevHash         string
	Version          int
}

// TableName returns record file table name
func (RecordFile) TableName() string {
	return tableNameRecordFile
}
