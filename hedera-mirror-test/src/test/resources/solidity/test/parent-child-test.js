/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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
const chai = require("chai");
chai.use(require('chai-as-promised'))

const expect = chai.expect;
const { ethers, waffle } = require("hardhat");
const provider = waffle.provider;

describe("ParentChild", function () {
  it("Should contain zero balances on deployment", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy();
    await parent.deployed();

    // verify passed in defaults
    expect(await provider.getBalance(parent.address)).to.equal(0);
  });

  it("Should contain non zero parent balance on funded deployment", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.1") });
    await parent.deployed();

    // verify passed in defaults
    expect(await provider.getBalance(parent.address)).to.equal(100000000000000000n);
  });

  it("Should contain zero child balance post createChild", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.4") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(0);

    // wait until the transaction is mined
    var contractFunctionResult = await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await provider.getBalance(contractFunctionResult.to)).to.equal(400000000000000000n); // parent from transaction
  });

  it("Should contain non zero child balance post createChild with transfer amount", async function () {
    const Parent = await ethers.getContractFactory("contracts/Parent.sol:Parent");
    const Child = await ethers.getContractFactory("contracts/Parent.sol:Child");
    const parent = await Parent.deploy({ value: ethers.utils.parseEther("0.4") });
    await parent.deployed();

    const [account1] = await ethers.getSigners();

    const submitCreateChildTx = await parent.connect(account1).createChild(5);

    // wait until the transaction is mined
    var contractFunctionResult = await submitCreateChildTx.wait();

    // verify updated emergent defaults
    expect(await provider.getBalance(contractFunctionResult.to)).to.equal(399999999999999995n); // parent from transaction
    const event0 = contractFunctionResult.events[0];
    expect(event0.args[0]).to.equal('Created second child contract');
    expect(event0.event).to.equal('ParentActivityLog');
    expect(event0.logIndex).to.equal(0);

    const event1 = contractFunctionResult.events[1];
    expect(event1.logIndex).to.equal(1);
    expect(event1.data).to.equal('0x0000000000000000000000000000000000000000000000000000000000000005');
    
    var childAddress = event1.address;
    expect(ethers.utils.isAddress(childAddress)).to.equal(true);
    expect(await provider.getBalance(childAddress)).to.equal(5); // child from transaction
  });
});
