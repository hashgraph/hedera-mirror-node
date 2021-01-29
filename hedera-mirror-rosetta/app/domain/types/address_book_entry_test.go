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

package types

import (
    "github.com/coinbase/rosetta-sdk-go/types"
    entityid "github.com/hashgraph/hedera-mirror-node/hedera-mirror-rosetta/app/domain/services/encoding"
    "github.com/stretchr/testify/assert"
    "reflect"
    "testing"
)

func exampleAddressBookEntries() *AddressBookEntries {
    return &AddressBookEntries{
        []*AddressBookEntry{
            newDummyAddressBookEntry(0, 0, 1, dummyMetadata("someip", "someport")),
            newDummyAddressBookEntry(0, 0, 2, dummyMetadata("someip2", "someport2")),
            newDummyAddressBookEntry(0, 1, 3, dummyMetadata("someip3", "someport3")),
            newDummyAddressBookEntry(10, 1, 3, dummyMetadata("someip3", "someport3")),
        },
    }
}

func expectedRosettaPeers() []*types.Peer {
    return []*types.Peer{
        newDummyPeer(0, 0, 1, dummyMetadata("someip", "someport")),
        newDummyPeer(0, 0, 2, dummyMetadata("someip2", "someport2")),
        newDummyPeer(0, 1, 3, dummyMetadata("someip3", "someport3")),
        newDummyPeer(10, 1, 3, dummyMetadata("someip3", "someport3")),
    }
}

func TestToRosettaPeers(t *testing.T) {
    // when:
    result := exampleAddressBookEntries().ToRosetta()

    // then:
    assert.Equal(t, len(expectedRosettaPeers()), len(result))

    // and:
    for _, a := range result {
        found := false
        for _, e := range result {
            if reflect.DeepEqual(a, e) {
                found = true
                break
            }
        }
        assert.True(t, found)
    }
}

func newDummyPeer(shard, realm, entity int64, metadata map[string]interface{}) *types.Peer {
    return &types.Peer{
        PeerID: (&Account{
            entityid.EntityId{
                ShardNum:  shard,
                RealmNum:  realm,
                EntityNum: entity,
            },
        }).String(),
        Metadata: metadata,
    }
}

func newDummyAddressBookEntry(shard, realm, entity int64, metadata map[string]interface{}) *AddressBookEntry {
    return &AddressBookEntry{
        PeerId: &Account{
            entityid.EntityId{
                ShardNum:  shard,
                RealmNum:  realm,
                EntityNum: entity,
            },
        },
        Metadata: metadata,
    }
}

func dummyMetadata(ip, port string) map[string]interface{} {
    return map[string]interface {
    }{
        "ip":   ip,
        "port": port,
    }
}
