/*-
 * ‌
 * Hedera Mirror Node
 *
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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
 * ‍
 */

package repository

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/types"
)

var MBlockRepository *MockBlockRepository
var MTransactionRepository *MockTransactionRepository
var MAccountRepository *MockAccountRepository
var MAddressBookEntryRepository *MockAddressBookEntryRepository

var NilBlock *types.Block = nil
var NilError *rTypes.Error = nil
var NilAmount *types.Amount = nil
var NilTransaction *types.Transaction = nil
var NilEntries *types.AddressBookEntries = nil

func Setup() {
	MBlockRepository = &MockBlockRepository{}
	MTransactionRepository = &MockTransactionRepository{}
	MAccountRepository = &MockAccountRepository{}
	MAddressBookEntryRepository = &MockAddressBookEntryRepository{}
}
