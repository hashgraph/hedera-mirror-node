// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

/**
 * Child contract of parent
 * Supports receiving of funds and ability to spend and report its balance
 *
 */
contract Child {
    function getAmount() public view returns (uint256) {
        return address(this).balance;
    }

    function spend(uint256 _amount) public returns (bool) {
        require(address(this).balance >= _amount);
        payable(address(0)).transfer(_amount);
        emit Transfer(address(this), address(0), _amount);
        return true;
    }

    receive() external payable {}

    event Transfer(address indexed _from, address indexed _to, uint256 amount);
}

/**
 * Contract to showcase a parent child contract relationship
 * On creation parent creates one sub child contract. Parent can create at most one more child for simplicity.
 * Parent supports a payable constructor and donate function to supply additional funds to children.
 * On creation of second child a transfer amount cna be specified to fund second child contract
 * Parent contract supports retrieval of address and balance of child contracts
 *
 */
contract Parent {
    address[2] childAddresses;

    constructor() payable {
        Child firstChild = new Child();
        childAddresses[0] = address(firstChild);
        emit ParentActivityLog("Created first child contract");
    }

    function createChild(uint256 amount) public {
        childAddresses[1] = address(new Child());
        emit ParentActivityLog("Created second child contract");

        if (amount > 0) {
            payable(childAddresses[1]).transfer(amount);
        }
    }

    function donate() public payable {
        emit Transfer(msg.sender, address(this), msg.value);
    }

    function transferToChild(uint256 amount, uint64 childIndex)
        public
        returns (bool)
    {
        require(address(this).balance >= amount);
        require(childIndex == 0 || childIndex == 1);
        address childAddress = childAddresses[childIndex];
        payable(childAddress).transfer(amount);
        emit Transfer(address(this), childAddress, amount);
        return true;
    }

    function getBalance() public view returns (uint256) {
        return address(this).balance;
    }

    function getChildAddress(uint64 childIndex) public view returns (address) {
        require(childIndex == 0 || childIndex == 1);
        return childIndex == 0 ? childAddresses[0] : childAddresses[1];
    }

    function getChildBalance(uint64 childIndex) public view returns (uint256) {
        Child myChild = Child(payable(getChildAddress(childIndex)));
        return myChild.getAmount();
    }

    receive() external payable {}

    event ParentActivityLog(string message);
    event Transfer(address indexed _from, address indexed _to, uint256 amount);
}
