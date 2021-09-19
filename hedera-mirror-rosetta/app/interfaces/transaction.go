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

package interfaces

import "github.com/hashgraph/hedera-sdk-go/v2"

// Transaction defines the transaction methods used by constructor service
type Transaction interface {
	Execute(client *hedera.Client) (hedera.TransactionResponse, error)
	GetNodeAccountIDs() []hedera.AccountID
	GetSignatures() (map[hedera.AccountID]map[*hedera.PublicKey][]byte, error)
	GetTransactionHash() ([]byte, error)
	GetTransactionID() hedera.TransactionID
	ToBytes() ([]byte, error)
	String() string
}
