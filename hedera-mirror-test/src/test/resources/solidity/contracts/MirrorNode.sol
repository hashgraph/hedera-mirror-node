// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/**
 * Mirror node contract receives parsed transactions of given shard and stores for querying.
 * Serves up transaction info for a query fee
 */
contract MirrorNodeShard {
    address payable private owner;

    mapping(uint256 => string) transactions;

    MirrorNode private parentContract;

    uint8 private shard;
    uint256 private queryFee;
    uint256 private totalFeesCollected;
    uint256 private totalQueriesReceived;
    uint256 private transactionStoredCount;
    uint256 private maxTransactionsCount;

    constructor(
        address _owner,
        address _parent,
        uint256 _queryFee,
        uint8 _shard,
        uint256 _maxTransactionsCount
    ) {
        parentContract = MirrorNode(_parent);
        queryFee = _queryFee;
        shard = _shard;
        owner = payable(_owner);
        maxTransactionsCount = _maxTransactionsCount;
        totalFeesCollected = 0;
        totalQueriesReceived = 0;
    }

    function getTotalFeesCollected() public view returns (uint256) {
        require(
            msg.sender == owner,
            "MirrorNodeShard: getTotalFeesCollected non owner checking fees collected"
        );
        return totalFeesCollected;
    }

    function getShard() public view returns (uint8) {
        return shard;
    }

    function getTotalQueriesReceived() public view returns (uint256) {
        return totalQueriesReceived;
    }

    function getTransactionStoredCount() public view returns (uint256) {
        return transactionStoredCount;
    }

    function storeTransaction(uint256 timestamp, string memory transaction)
        external
        payable
        returns (bool)
    {
        emit StoreTransactionLog(
            shard,
            timestamp,
            string(
                abi.encodePacked("called storeTransaction with ", transaction)
            )
        );
        // require(
        //     transactionStoredCount + 1 <= maxTransactionsCount,
        //     "MirrorNodeShard: storeTransaction max transaction count"
        // );
        // emit StoreTransactionLog(
        //     shard,
        //     timestamp,
        //     "transaction count is acceptable"
        // );

        // // update counters and mappings
        // transactions[timestamp] = transaction;
        // transactionStoredCount += 1;
        // emit StoreTransactionLog(
        //     shard,
        //     timestamp,
        //     "stored and updated mappings"
        // );

        // emit TransactionStorage(shard, timestamp, address(this));
        return true;
    }

    function getTransactionInfo(uint256 timestamp)
        public
        payable
        returns (string memory)
    {
        require(
            msg.value >= queryFee,
            "MirrorNodeShard: getTransactionInfo gas exceeds networkFee"
        );

        string memory transaction = transactions[timestamp];
        require(
            bytes(transaction).length > 0,
            "MirrorNodeShard: transaction contents exceed 0 bytes"
        );

        // update counters
        totalFeesCollected += msg.value;
        totalQueriesReceived += 1;

        emit TransactionRequest(shard, timestamp, address(this));
        return transaction;
    }

    // allow mirror node mainter to withdraw fees paid to them for storing and serving up transactions
    function withdraw(uint256 amount) public {
        require(msg.sender == owner, "Not owner");
        owner.transfer(amount);
    }

    // receive() external payable {
    // }

    event TransactionStorage(
        uint8 shard,
        uint256 timestamp,
        address indexed mirror
    );
    event TransactionRequest(
        uint8 shard,
        uint256 timestamp,
        address indexed mirror
    );

    event StoreTransactionLog(uint8 shard, uint256 timestamp, string message);
}

/**
 * Receives network transactions, parses them and passes them on to enrolled mirror node.
 * Recieves an enrollment fee when mirror node enroll as well as a percentage of paid fees for mirror node query payment
 *
 * Note: Contract does not yet implement full ERC 20 logic, allowance and approval logic is missing
 */
contract MirrorNode {
    address payable private owner;

    mapping(uint8 => address) private shardAddresses;
    mapping(uint256 => uint8) private timestampShards;

    uint256 private apiFee;
    uint8 private maxShardCount;
    uint256 private maxTransactionsPerShard;
    uint8 private shardCount;
    uint256 private transactionCount;
    uint256 private storageFee;

    constructor(
        uint8 _maxShardCount,
        uint256 _maxTransactionsPerShard,
        uint256 _storageFee,
        uint256 _apiFee
    ) {
        maxShardCount = _maxShardCount;
        maxTransactionsPerShard = _maxTransactionsPerShard;
        storageFee = _storageFee;
        apiFee = _apiFee;
        transactionCount = 0;
        shardCount = 0;
        owner = payable(msg.sender);
    }

    function getMaxShardCount() public view returns (uint256) {
        return maxShardCount;
    }

    function getMaxTransactionsPerShard() public view returns (uint256) {
        return maxTransactionsPerShard;
    }

    function getShardCount() public view returns (uint256) {
        return shardCount;
    }

    function getShardAddress(uint8 shard) public view returns (address) {
        require(
            shard <= shardCount,
            "MirrorNode: getShardAddress with out of range shard integer"
        );

        return shardAddresses[shard];
    }

    function getTransactionShard(uint256 timestamp)
        public
        view
        returns (uint8)
    {
        require(
            timestamp >= 0,
            "MirrorNode: getTransactionShard with out of range timestamp"
        );

        return timestampShards[timestamp];
    }

    function getTransactionCount() public view returns (uint256) {
        return transactionCount;
    }

    function getStorageFee() public view returns (uint256) {
        return storageFee;
    }

    function getApiFee() public view returns (uint256) {
        return apiFee;
    }

    /**
     * Simple storage function.
     * A few edge cases remain, such as duplicate transction handling, ensuring timestampt increase with time etc
     */
    function submitTransaction(
        uint256 timestamp,
        string memory transaction,
        uint8 shard
    ) public payable returns (bool) {
        emit SubmitTransactionLog(
            shard,
            timestamp,
            string(
                abi.encodePacked("submitTransaction called with ", transaction)
            )
        );
        // require(
        //     msg.value >= storageFee,
        //     "MirrorNode: submitTransaction with inadequate storageFee payment"
        // );
        // emit SubmitTransactionLog(shard, timestamp, "storageFee acceptable");
        // require(
        //     msg.sender != address(0),
        //     "MirrorNode: submitTransaction from the zero address"
        // );
        // emit SubmitTransactionLog(shard, timestamp, "address acceptable");
        // require(
        //     timestamp > 0,
        //     "MirrorNode: timestamp should be greater than 0"
        // );
        // emit SubmitTransactionLog(shard, timestamp, "timestamp acceptable");

        // // check if shard contract exist, if not create and cache address
        // if (shardAddresses[shard] == address(0)) {
        //     emit SubmitTransactionLog(
        //         shard,
        //         timestamp,
        //         "shard doesn't exist, will create"
        //     );
        //     shardAddresses[shard] = address(
        //         new MirrorNodeShard(
        //             owner,
        //             address(this),
        //             apiFee,
        //             shard,
        //             maxTransactionsPerShard
        //         )
        //     );
        //     emit SubmitTransactionLog(
        //         shard,
        //         timestamp,
        //         string(
        //             abi.encodePacked(
        //                 "new MirrorNodeShard contract create at ",
        //                 shardAddresses[shard]
        //             )
        //         )
        //     );

        //     shardCount += 1;
        //     emit NewMirrorNodeShard(shard, timestamp);
        // }

        // // pass transaction to appropriate shard
        // address mirrorAddress = shardAddresses[shard];
        // emit SubmitTransactionLog(
        //     shard,
        //     timestamp,
        //     string(
        //         abi.encodePacked(
        //             "storing new transaction in MirrorNodeShard at ",
        //             mirrorAddress
        //         )
        //     )
        // );
        // MirrorNodeShard(mirrorAddress).storeTransaction{value: msg.value / 2}(
        //     timestamp,
        //     transaction
        // );
        // emit SubmitTransactionLog(shard, timestamp, "stored transaction");

        // // update counters and mappings
        // timestampShards[timestamp] = shard;
        // transactionCount += 1;
        // emit SubmitTransactionLog(
        //     shard,
        //     timestamp,
        //     "updated counts and mappings"
        // );

        // share half the fee with shard for storage maintenance
        // payable(mirrorAddress).transfer(msg.value / 2);

        emit TransactionParsed(timestamp, shard);
        return true;
    }

    function submitTransactionSimple(
        uint256 timestamp,
        string memory transaction,
        uint8 shard
    ) public returns (bool) {
        emit SubmitTransactionLog(
            shard,
            timestamp,
            string(
                abi.encodePacked("submitTransaction called with ", transaction)
            )
        );

        emit TransactionParsed(timestamp, shard);
        return true;
    }

    // allow mirror node mainter to withdraw fees paid to them for parsing and storing transactions
    function withdraw(uint256 amount) public {
        require(msg.sender == owner, "Not owner");
        owner.transfer(amount);
    }

    event TransactionParsed(uint256 timestamp, uint8 shard);
    event NewMirrorNodeShard(uint8 shard, uint256 timestamp);
    event SubmitTransactionLog(uint8 shard, uint256 timestamp, string message);
}
