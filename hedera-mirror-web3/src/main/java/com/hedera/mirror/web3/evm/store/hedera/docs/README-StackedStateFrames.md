#`StackedStateFrames` and `UpdatableReferenceCache`

A few diagrams to help understand what's going on here with `StackedStateFrames` and `UpdatableReferenceCache`.

_Important note:_ These caches cache _references_ to things. They should really, really only be used to cache _immutable value-like_ entities. Otherwise, if you actually _modify_ an entities' state you'll modify it not only in the top-level cache but in the lower-caches _which breaks the "commit" feature_. The implementation will attempt to detect, at runtime, that you're trying to update an entry with the same value (i.e., reference) that's already in the cache - which probably indicates that you modified the state and are attempting to update the cache with it.

- An alternate solution would be to immediately _wrap_ underlying R/W entities in R/O wrappers and then use them exclusively.

<!--
Once these diagrams are _fixed_ use the method at https://stackoverflow.com/a/32771815/751579 to get nicer SVG images - problem is that with that technique you have to update this document every time the images change - problem with _this_ technique is that you have to update this document _after_  the merge to the main branch, which is arguably worse.
-->

## Object diagram for a contract call with an inner call

![Object diagram for a contract call with an inner call](http://www.plantuml.com/plantuml/proxy?cache-no&src=https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/framed-state-experiment/hedera-mirror-web3/src/main/java/com/hedera/mirror/web3/evm/store/hedera/docs/ObjectDiagram-StackedStateFrames.puml)

<div style="page-break-before:always"></div>

## Class diagram of `StackedStateFrames` components

![Class diagram of StackedStateFrames components ](http://www.plantuml.com/plantuml/proxy?cache-no&src=https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/framed-state-experiment/hedera-mirror-web3/src/main/java/com/hedera/mirror/web3/evm/store/hedera/docs/ClassDiagram-StackedStateFrames.puml)

<div style="page-break-before:always"></div>

## State diagram for `UpdatableReferenceCache`

![State diagram for UpdatableReferenceCache](http://www.plantuml.com/plantuml/proxy?cache-no&src=https://raw.githubusercontent.com/hashgraph/hedera-mirror-node/framed-state-experiment/hedera-mirror-web3/src/main/java/com/hedera/mirror/web3/evm/store/hedera/docs/StateDiagram-UpdatableReferenceCache.puml)

<div style="page-break-before:always"></div>

## Decision diagram for `UpdateableReferenceCache`

### _(method,state) ⮕ action_

![Decision diagram](DecisionDiagram-UpdatableReferenceCache.png)
