// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract CallContract {
    function makeCallWithoutAmount(address _to, bytes memory _data)
    public
    returns (bool success, bytes memory returnData)
    {
        (success, returnData) = _to.call(_data);
    }

    function makeCallWithAmount(address _to, bytes memory _data)
    public payable
    returns (bool success, bytes memory returnData)
    {
        (success, returnData) = _to.call{value: msg.value}(_data);
    }

    function makeStaticCall(address _to, bytes memory _data)
    public
    returns (bool success, bytes memory returnData)
    {
        (success, returnData) = _to.staticcall(_data);
    }

    function makeDelegateCall(address _to, bytes memory _data)
    public
    returns (bool success, bytes memory returnData)
    {
        (success, returnData) = _to.delegatecall(_data);
    }

    function callCodeToContractWithoutAmount(address _address, bytes4 _sig) public returns (address) {
        bytes memory result;
        bool success;

        assembly {
            let x := mload(0x40)
            mstore(x, _sig)

            success := callcode(50000, _address, 0, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }

        return abi.decode(result, (address));
    }

    function callCodeToContractWithAmount(address _address, bytes4 _sig) public payable returns (address) {
        bytes memory result;
        bool success;

        assembly {
            let x := mload(0x40)
            mstore(x, _sig)

            let callValue := callvalue()
            success := callcode(50000, _address, callValue, x, 0x4, x, 0x20)

            mstore(0x40, add(x, 0x20))
            mstore(result, x)
        }

        return abi.decode(result, (address));
    }
}
