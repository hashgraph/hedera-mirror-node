const chai = require("chai");
chai.use(require('chai-as-promised'))

const expect = chai.expect;
const { ethers } = require("hardhat");

describe("ParentChild", function () {
  it("Should set defaults on deployment", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.1") });
    await parent.deployed();

    // verify passed in defaults
    expect(await parent.getBalance()).to.equal(100000000000000000n);
  });

  it("Should contain non zero parent balance post donate", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.2") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitDonateTx = await parent.connect(account1).donate({ value: ethers.utils.parseEther("0.3") });

    // wait until the transaction is mined
    await submitDonateTx.wait();

    // verify updated emergent defaults
    expect(await parent.getBalance()).to.equal(500000000000000000n);
  });

  it("Should contain zero child balance post createChild", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.4") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild();

    // wait until the transaction is mined
    await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await parent.getChildBalance()).to.equal(0);
  });

  it("Should contain non zero child balance post transfer", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.6") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild();
    await submitCreateChildTx.wait();

    const transferToChildTx = await parent.connect(account1).transferToChild(1);
    await transferToChildTx.wait();

    // verify updated emergent defaults
    expect(await parent.getBalance()).to.equal(599999999999999999n);
    expect(await parent.getChildBalance()).to.equal(1);
  });

  it("Should contain non zero child balance post transfer when child is queried", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.7") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild();
    await submitCreateChildTx.wait();

    const transferToChildTx = await parent.connect(account1).transferToChild(1);
    await transferToChildTx.wait();

    // verify query call
    var address = await parent.getChildAddress()
    const Child = await ethers.getContractFactory("contracts/Parent.sol:Child");
    const child = Child.attach(address);
    
    expect(await child.getAmount()).to.equal(1);
  });

  it("Should update child balance upon spending", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.6") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild();
    await submitCreateChildTx.wait();

    const transferToChildTx = await parent.connect(account1).transferToChild(ethers.utils.parseEther("0.2"));
    await transferToChildTx.wait();

    var address = await parent.getChildAddress()
    const Child = await ethers.getContractFactory("contracts/Parent.sol:Child");
    const child = Child.attach(address);

    const childSpendTx = await child.spend(ethers.utils.parseEther("0.1"));
    await childSpendTx.wait();

    // verify updated emergent defaults
    expect(await parent.getChildBalance()).to.equal(ethers.utils.parseEther("0.1"));
  });
});