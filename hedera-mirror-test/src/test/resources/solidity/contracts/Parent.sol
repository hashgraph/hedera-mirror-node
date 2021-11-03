// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract Child {
    function getAmount() public view returns (uint256) {
        return address(this).balance;
    }

    receive() external payable {}
}

contract Parent {
    Child private myChild;

    constructor() {}

    function createChild() public {
        myChild = new Child();
        emit ParentActivityLog("Created child contract");
    }

    function donate() public payable {
        emit ParentActivityLog(
            string(abi.encodePacked("Accepted donation ", msg.value))
        );
    }

    function transferToChild(uint256 _amount) public returns (bool) {
        require(address(this).balance >= _amount);
        address childAddress = address(myChild);
        payable(childAddress).transfer(_amount);
        emit ParentActivityLog(
            string(
                abi.encodePacked(
                    "Successfully transferred ",
                    _amount,
                    " to child contract ",
                    childAddress
                )
            )
        );
        return true;
    }

    function getBalance() public view returns (uint256) {
        return address(this).balance;
    }

    function getChildBalance() public view returns (uint256) {
        return myChild.getAmount();
    }

    receive() external payable {}

    event ParentActivityLog(string message);
}
