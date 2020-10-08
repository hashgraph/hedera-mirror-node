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

package transaction

import (
	"reflect"
	"testing"
)

func TestFilterTransactionsForRange(t *testing.T) {
	var testData = []struct {
		transactions   []transaction
		consensusStart int64
		consensusEnd   int64
	}{
		{[]transaction{{ConsensusNS: 50}, {ConsensusNS: 60}}, 10, 100},
		{[]transaction{{ConsensusNS: 10}, {ConsensusNS: 20}}, 10, 100},
		{[]transaction{{ConsensusNS: 50}, {ConsensusNS: 100}}, 10, 100},
		{[]transaction{{ConsensusNS: 1}, {ConsensusNS: 50}}, 10, 100},
		{[]transaction{{ConsensusNS: 1}, {ConsensusNS: 2}}, 10, 100},
		{[]transaction{{ConsensusNS: 101}, {ConsensusNS: 102}}, 10, 100},
	}

	var expectedTransactions = []struct {
		transactions []transaction
	}{
		{[]transaction{{ConsensusNS: 50}, {ConsensusNS: 60}}},
		{[]transaction{{ConsensusNS: 10}, {ConsensusNS: 20}}},
		{[]transaction{{ConsensusNS: 50}, {ConsensusNS: 100}}},
		{[]transaction{{ConsensusNS: 50}}}, // 1 is filtered
		{[]transaction{}},                  // both are filtered
		{[]transaction{}},                  // both are filtered
	}

	for i, tt := range testData {
		res := filterTransactionsForRange(tt.transactions, tt.consensusStart, tt.consensusEnd)
		if !reflect.DeepEqual(res, expectedTransactions[i].transactions) {
			t.Errorf("Got %d, expected %d", res, expectedTransactions[i].transactions)
		}
	}
}
