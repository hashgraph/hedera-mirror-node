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
