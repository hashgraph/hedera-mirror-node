package services

import (
	"fmt"
	"math"
	"testing"
)

func TestEntityIdEncoding(t *testing.T) {
	var testData = []struct {
		shard, realm, number, expected int64
	}{
		{0, 0, 0, 0},
		{0, 0, 10, 10},
		{0, 0, 4294967295, 4294967295},
		{32767, 65535, 4294967295, 9223372036854775807},
		{32767, 0, 0, 9223090561878065152},
	}

	for _, tt := range testData {
		testname := fmt.Sprintf("%d.%d.%d", tt.shard, tt.realm, tt.number)
		t.Run(testname, func(t *testing.T) {
			res, _ := Encode(tt.shard, tt.realm, tt.number)
			if res != tt.expected {
				t.Errorf("Got %d, expected %d", res, tt.expected)
			}
		})
	}
}

func TestEntityIdEncodeThrows(t *testing.T) {
	var testData = []struct {
		shard, realm, number int64
	}{
		{int64(math.MaxInt16 + 1), 0, 0},  // 1 << shardBits
		{0, int64(math.MaxUint16 + 1), 0}, // 1 << realmBits
		{0, 0, int64(math.MaxUint32 + 1)}, // 1 << numberBits
		{-1, 0, 0},
		{0, -1, 0},
		{0, 0, -1},
	}

	for _, tt := range testData {
		testname := fmt.Sprintf("%d.%d.%d", tt.shard, tt.realm, tt.number)
		t.Run(testname, func(t *testing.T) {
			_, err := Encode(tt.shard, tt.realm, tt.number)
			if err == nil {
				t.Errorf("Expected error when providing invalid encoding parameters")
			}
		})
	}
}

func TestEntityIdDecoding(t *testing.T) {
	var testData = []struct {
		input, shard, realm, number int64
	}{
		{0, 0, 0, 0},
		{10, 0, 0, 10},
		{4294967295, 0, 0, 4294967295},
		{2814792716779530, 10, 10, 10},
		{9223372036854775807, 32767, 65535, 4294967295},
		{9223090561878065152, 32767, 0, 0},
	}

	for _, tt := range testData {
		testname := fmt.Sprintf("%d.%d.%d", tt.shard, tt.realm, tt.number)
		t.Run(testname, func(t *testing.T) {
			res, _ := Decode(tt.input)
			if res.ShardNum != tt.shard ||
				res.RealmNum != tt.realm ||
				res.EntityNum != tt.number {
				t.Errorf("Got %d.%d.%d, expected %d.%d.%d", res.ShardNum, res.RealmNum, res.EntityNum, tt.shard, tt.realm, tt.number)
			}
		})
	}
}

func TestEntityIdDecodeThrows(t *testing.T) {
	_, err := Decode(-1)
	if err == nil {
		t.Errorf("Expected error when providing invalid encoding parameters")
	}
}
