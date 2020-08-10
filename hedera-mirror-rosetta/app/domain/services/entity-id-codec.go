package services

import (
	"fmt"
)

const (
	shardBits  int   = 15
	realmBits  int   = 16
	numberBits int   = 32
	shardMask  int64 = (int64(1) << shardBits) - 1
	realmMask  int64 = (int64(1) << realmBits) - 1
	numberMask int64 = (int64(1) << numberBits) - 1
)

// DecodedData returns the decoded data from the DB ID
type DecodedData struct {
	ShardNum  int64
	RealmNum  int64
	EntityNum int64
}

// Encode - encodes the shard, realm and entity id into a Entity DB Id
func Encode(shardNum int64, realmNum int64, entityNum int64) (int64, error) {
	if shardNum > shardMask || shardNum < 0 ||
		realmNum > realmMask || realmNum < 0 ||
		entityNum > numberMask || entityNum < 0 {
		return 0, fmt.Errorf("Invalid parameters provided for encoding: %d.%d.%d", shardNum, realmNum, entityNum)
	}
	return (entityNum & numberMask) |
		(realmNum&realmMask)<<numberBits |
		(shardNum&shardMask)<<(realmBits+numberBits), nil
}

// Decode - decodes the Entity DB id into Account struct
func Decode(encodedID int64) (*DecodedData, error) {
	if encodedID < 0 {
		return nil, fmt.Errorf("encodedID cannot be negative: %d", encodedID)
	}

	return &DecodedData{
		ShardNum:  encodedID >> (realmBits + numberBits),
		RealmNum:  (encodedID >> numberBits) & realmMask,
		EntityNum: encodedID & numberMask,
	}, nil
}
