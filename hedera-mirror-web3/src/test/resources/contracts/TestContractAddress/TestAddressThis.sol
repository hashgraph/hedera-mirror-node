pragma solidity 0.8.18;

contract TestAddressThis {

    constructor() payable {
        address test = address(this);
        if (test == address(0)) {
            revert("Zero address.");
        }
    }

    function testAddressThis() public {
        address test = address(this);
        if (test == address(0)) {
            revert("Zero address.");
        }
    }

}