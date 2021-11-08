const chai = require("chai");
chai.use(require('chai-as-promised'))

const expect = chai.expect;
const { ethers } = require("hardhat");

describe("ParentChild", function () {
  it("Should contain zero balances on deployment", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy();
    await parent.deployed();

    // verify passed in defaults
    expect(await parent.getBalance()).to.equal(0);
    expect(await parent.getChildBalance(0)).to.equal(0);    
  });

  it("Should contain non zero parent balance on funded deployment", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.1") });
    await parent.deployed();

    // verify passed in defaults
    expect(await parent.getBalance()).to.equal(100000000000000000n);
    expect(await parent.getChildBalance(0)).to.equal(0);    
  });

  it("Should reflect increase balance on parent balance post donate", async function () {
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

    const submitCreateChildTx = await parent.connect(account1).createChild(0);

    // wait until the transaction is mined
    await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await parent.getChildBalance(1)).to.equal(0);
  });

  it("Should contain non zero child balance post createChild with transfer amount", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.4") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(5);

    // wait until the transaction is mined
    await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await parent.getChildBalance(1)).to.equal(5);
  });

  it("Should contain non zero child balances post transfer", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.6") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(0);
    await submitCreateChildTx.wait();

    const transferToFirstChildTx = await parent.connect(account1).transferToChild(1, 0);
    await transferToFirstChildTx.wait();

    const transferToSecondChildTx = await parent.connect(account1).transferToChild(2, 1);
    await transferToSecondChildTx.wait();

    // verify
    expect(await parent.getBalance()).to.equal(599999999999999997n);

    // contract to contract call
    expect(await parent.getChildBalance(0)).to.equal(1);
    expect(await parent.getChildBalance(1)).to.equal(2);

    // child contract calls
    var firstAddress = await parent.getChildAddress(0);
    var secondAddress = await parent.getChildAddress(1);
    const Child = await ethers.getContractFactory("contracts/Parent.sol:Child");
    const firstChild = Child.attach(firstAddress);
    const secondChild = Child.attach(secondAddress);
    
    expect(await firstChild.getAmount()).to.equal(1);
    expect(await secondChild.getAmount()).to.equal(2);
  });

  it("Should update child balance upon spending", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.6") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(0);
    await submitCreateChildTx.wait();

    const transferToFirstChildTx = await parent.connect(account1).transferToChild(ethers.utils.parseEther("0.2"), 0);
    await transferToFirstChildTx.wait();

    const transferToSecondChildTx = await parent.connect(account1).transferToChild(ethers.utils.parseEther("0.3"), 1);
    await transferToSecondChildTx.wait();

    var firstAddress = await parent.getChildAddress(0);
    var secondAddress = await parent.getChildAddress(1);
    const Child = await ethers.getContractFactory("contracts/Parent.sol:Child");
    const firstChild = Child.attach(firstAddress);
    const secondChild = Child.attach(secondAddress);

    const firstChildSpendTx = await firstChild.spend(ethers.utils.parseEther("0.1"));
    await firstChildSpendTx.wait();

    const secondChildSpendTx = await secondChild.spend(ethers.utils.parseEther("0.1"));
    await secondChildSpendTx.wait();

    // verify updated emergent defaults
    expect(await parent.getChildBalance(0)).to.equal(ethers.utils.parseEther("0.1"));
    expect(await parent.getChildBalance(1)).to.equal(ethers.utils.parseEther("0.2"));
  });
});