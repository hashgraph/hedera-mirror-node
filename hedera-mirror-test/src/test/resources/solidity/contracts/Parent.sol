// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

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

contract Parent {
    Child private myChild;

    constructor() payable {}

    function createChild() public {
        myChild = new Child();
        emit ParentActivityLog("Created child contract");
    }

    function donate() public payable {
        emit Transfer(msg.sender, address(this), msg.value);
    }

    function transferToChild(uint256 _amount) public returns (bool) {
        require(address(this).balance >= _amount);
        address childAddress = address(myChild);
        payable(childAddress).transfer(_amount);
        emit Transfer(address(this), childAddress, _amount);
        return true;
    }

    function getBalance() public view returns (uint256) {
        return address(this).balance;
    }

    function getChildAddress() public view returns (address) {
        return address(myChild);
    }

    function getChildBalance() public view returns (uint256) {
        return myChild.getAmount();
    }

    receive() external payable {}

    event ParentActivityLog(string message);
    event Transfer(address indexed _from, address indexed _to, uint256 amount);
}
