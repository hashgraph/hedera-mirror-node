const chai = require("chai");
chai.use(require('chai-as-promised'))

const expect = chai.expect;
const { ethers } = require("hardhat");

describe("MirrorNode", function () {
  it("Should set defaults on deployment", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    // verify passed in defaults
    expect(await mirrornode.getMaxShardCount()).to.equal(3);
    expect(await mirrornode.getMaxTransactionsPerShard()).to.equal(100);
    expect(await mirrornode.getStorageFee()).to.equal(5);
    expect(await mirrornode.getApiFee()).to.equal(1);

    // verify emergent defaults
    expect(await mirrornode.getShardCount()).to.equal(0);
    expect(await mirrornode.getTransactionCount()).to.equal(0);
  });

  it("Should not contain valid shards and transactions post deployment", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    // verify non existense of shards and transactions
    await expect(mirrornode.getShardAddress(1)).to.be.rejectedWith('MirrorNode: getShardAddress with out of range shard integer');
    expect(await mirrornode.getTransactionShard(1234)).to.equal(0);
  });

  it("Should fail when submitTransaction called with insufficient gas", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    await expect(mirrornode.submitTransaction(1234, "CRYPTOTRANSFER", 1)).to.be.rejectedWith('MirrorNode: submitTransaction with inadequate storageFee payment');
  });

  it("Should contain valid shards and transactions post submitTransaction", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    const [account1] = await ethers.getSigners();

    const submitCryptoTransferTx = await mirrornode.connect(account1).submitTransaction(1234, "CRYPTOTRANSFER", 1, { value: ethers.utils.parseEther("0.5") });

    // wait until the transaction is mined
    await submitCryptoTransferTx.wait();

    // verify updated emergent defaults
    expect(await mirrornode.getShardCount()).to.equal(1);
    expect(await mirrornode.getTransactionCount()).to.equal(1);

    // verify existense of shards and stored transactions
    expect(await mirrornode.getShardAddress(1)).to.not.equal('');
    expect(await mirrornode.getTransactionShard(1234)).to.equal(1);
  });

  it("Should support multi shard scenario", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    const [account1, account2, account3] = await ethers.getSigners();

    const submitCryptoTransferTx = await mirrornode.connect(account1).submitTransaction(1234, "CRYPTOTRANSFER", 1, { value: ethers.utils.parseEther("0.5") });
    await submitCryptoTransferTx.wait();

    const submitTokenCreationTx = await mirrornode.connect(account2).submitTransaction(5678, "TOKENCREATION", 2, { value: ethers.utils.parseEther("0.5") });
    await submitTokenCreationTx.wait();

    const submitConsensusSubmitMessageTx = await mirrornode.connect(account3).submitTransaction(9012, "CONSENSUSSUBMITMESSAGE", 3, { value: ethers.utils.parseEther("0.5") });
    await submitConsensusSubmitMessageTx.wait();

    // verify updated emergent defaults
    expect(await mirrornode.getShardCount()).to.equal(3);
    expect(await mirrornode.getTransactionCount()).to.equal(3);

    // verify existense of shards and stored transactions
    expect(await mirrornode.getTransactionShard(1234)).to.equal(1);
    expect(await mirrornode.getTransactionShard(5678)).to.equal(2);
    expect(await mirrornode.getTransactionShard(9012)).to.equal(3);
  });

  it("Should fail when shard transaction query gas is insufficient", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    const [account1] = await ethers.getSigners();

    const submitCryptoTransferTx = await mirrornode.connect(account1).submitTransaction(1234, "CRYPTOTRANSFER", 1, { value: ethers.utils.parseEther("0.5")});
    await submitCryptoTransferTx.wait();

    // verify query call
    var address = await mirrornode.getShardAddress(1)
    const MirrorNodeShard = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNodeShard");
    const mirrornodeshard = MirrorNodeShard.attach(address);
    
    await expect(mirrornodeshard.getTransactionInfo(1234)).to.be.rejectedWith('MirrorNodeShard: getTransactionInfo gas exceeds networkFee');
  });

  it("Should support transaction query to shard", async function () {
    const MirrorNode = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNode");
    const mirrornode = await MirrorNode.deploy(3, 100, 5, 1);
    await mirrornode.deployed();

    const [account1] = await ethers.getSigners();

    const submitCryptoTransferTx = await mirrornode.connect(account1).submitTransaction(1234, "CRYPTOTRANSFER", 1, { value: ethers.utils.parseEther("0.5")});
    await submitCryptoTransferTx.wait();

    // verify query call
    var address = await mirrornode.getShardAddress(1)
    const MirrorNodeShard = await ethers.getContractFactory("contracts/MirrorNodeShards.sol:MirrorNodeShard");
    const mirrornodeshard = MirrorNodeShard.attach(address);


    expect(await mirrornodeshard.getTransactionStoredCount()).to.equal(1);
    expect(await mirrornodeshard.getTotalQueriesReceived()).to.equal(0);

    const getCryptoTransferTx = await mirrornodeshard.connect(account1).getTransactionInfo(1234, { value: ethers.utils.parseEther("0.5") });
    await getCryptoTransferTx.wait();

    expect(await mirrornodeshard.getTotalQueriesReceived()).to.equal(1);
  });
});