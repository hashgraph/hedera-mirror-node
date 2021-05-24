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

package common

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

const (
	amount    int64 = 100
	timestamp int64 = 200
	entityID  int64 = 300
)

func TestShouldReturnValidCryptoTransferTableName(t *testing.T) {
	assert.Equal(t, "crypto_transfer", CryptoTransfer{}.TableName())
}

func TestShouldReturnCryptoTransferAmount(t *testing.T) {
	cryptoTransfer := CryptoTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, amount, cryptoTransfer.GetAmount())
}

func TestShouldReturnCryptoTransferConsensusTimestamp(t *testing.T) {
	cryptoTransfer := CryptoTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, timestamp, cryptoTransfer.GetConsensusTimestamp())
}

func TestShouldReturnCryptoTransferEntityID(t *testing.T) {
	cryptoTransfer := CryptoTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, entityID, cryptoTransfer.GetEntityID())
}

func TestShouldReturnValidNonFeeTransferTableName(t *testing.T) {
	assert.Equal(t, "non_fee_transfer", NonFeeTransfer{}.TableName())
}

func TestShouldReturnNonFeeTransferAmount(t *testing.T) {
	nonFeeTransfer := NonFeeTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, amount, nonFeeTransfer.GetAmount())
}

func TestShouldReturnNonFeeTransferConsensusTimestamp(t *testing.T) {
	nonFeeTransfer := NonFeeTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, timestamp, nonFeeTransfer.GetConsensusTimestamp())
}

func TestShouldReturnNonFeeTransferEntityID(t *testing.T) {
	nonFeeTransfer := NonFeeTransfer{
		Amount:             amount,
		ConsensusTimestamp: timestamp,
		EntityID:           entityID,
	}

	assert.Equal(t, entityID, nonFeeTransfer.GetEntityID())
}
