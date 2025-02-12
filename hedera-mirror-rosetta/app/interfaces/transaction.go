/*
 * Copyright (C) 2019-2025 Hedera Hashgraph, LLC
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

package interfaces

import "github.com/hiero-ledger/hiero-sdk-go/v2/sdk"

// Transaction defines the transaction methods used by constructor service
// Remove the interface when SDK adds support of hiero.TransactionIsFrozen and
// hiero.TransactionGetDefaultMaxTransactionFee
type Transaction interface {

	// IsFrozen returns if the transaction is frozen
	IsFrozen() bool

	// GetDefaultMaxTransactionFee returns the default max transaction fee set for the Transaction
	GetDefaultMaxTransactionFee() hiero.Hbar
}
