package types

import (
	rTypes "github.com/coinbase/rosetta-sdk-go/types"
)

// AddressBookEntry is domain level struct used to represent Rosetta Peer
type AddressBookEntry struct {
	PeerId   *Account
	Metadata map[string]interface{}
}

// AddressBookEntries is domain level struct used to represent an array of AddressBookEntry
type AddressBookEntries struct {
	Entries []*AddressBookEntry
}

// ToRosettaPeer returns Rosetta type Peer from the current domain type AddressBookEntry
func (abe *AddressBookEntry) ToRosettaPeer() *rTypes.Peer {
	return &rTypes.Peer{
		PeerID:   abe.PeerId.String(),
		Metadata: abe.Metadata,
	}
}

// ToRosettaPeers returns an array of Rosetta type Peer
func (abe *AddressBookEntries) ToRosettaPeers() []*rTypes.Peer {
	peers := make([]*rTypes.Peer, len(abe.Entries))
	for i, e := range abe.Entries {
		peers[i] = e.ToRosettaPeer()
	}

	return peers
}
