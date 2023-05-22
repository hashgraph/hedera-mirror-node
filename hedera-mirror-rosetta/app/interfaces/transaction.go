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

package interfaces

import (
	"github.com/hashgraph/hedera-sdk-go/v2"
	"time"
)

// Transaction defines the transaction methods used by constructor service
type Transaction interface {

	// Execute submits the Transaction to the network using client
	Execute(client *hedera.Client) (hedera.TransactionResponse, error)

	// IsFrozen returns if the transaction is frozen
	IsFrozen() bool

	// GetDefaultMaxTransactionFee returns the default max transaction fee set for the Transaction
	GetDefaultMaxTransactionFee() hedera.Hbar

	// GetNodeAccountIDs returns the node accounts ids set for the Transaction
	GetNodeAccountIDs() []hedera.AccountID

	// GetSignatures returns the signatures of the Transaction
	GetSignatures() (map[hedera.AccountID]map[*hedera.PublicKey][]byte, error)

	// GetTransactionHash returns the transaction hash
	GetTransactionHash() ([]byte, error)

	// GetTransactionID returns the transaction id
	GetTransactionID() hedera.TransactionID

	// GetTransactionMemo returns the transaction memo
	GetTransactionMemo() string

	// GetTransactionValidDuration returns the transaction valid duration
	GetTransactionValidDuration() time.Duration

	// String encodes the Transaction to a string
	String() string

	// ToBytes serializes the Transaction to a byte slice
	ToBytes() ([]byte, error)
}
