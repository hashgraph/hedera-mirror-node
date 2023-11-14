// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract EquivalenceContract {
    function makeCallWithoutAmount(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call(_data);
    }

    function makeCallWithAmount(address _to, bytes memory _data) external payable returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.call{value: msg.value}(_data);
    }

    function makeStaticCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.staticcall(_data);
    }

    function makeDelegateCall(address _to, bytes memory _data) external returns (bool success, bytes memory returnData) {
        (success, returnData) = _to.delegatecall(_data);
    }

    function callCodeToContractWithoutAmount(address _address, bytes4 _sig) external returns (address) {
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

    function callCodeToContractWithAmount(address _address, bytes4 _sig) external payable returns (address) {
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

    function getBalance(address _address) external view returns (uint) {
        return _address.balance;
    }

    function getCodeSize(address _address) external view returns (uint256) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        return size;
    }

    function getCodeHash(address _address) external view returns (bytes32) {
        bytes32 codehash;
        assembly {
            codehash := extcodehash(_address)
        }
        return codehash;
    }

    function copyCode(address _address) external view returns (bytes memory) {
        uint256 size;
        assembly {
            size := extcodesize(_address)
        }
        bytes memory code = new bytes(size);

        assembly {
            extcodecopy(_address, add(code, 32), 0, size)
        }
        return code;
    }
}
