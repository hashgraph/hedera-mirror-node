// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

contract Reverter {
    error SomeCustomError();

    function revertPayable() public payable {
        revert("RevertReasonPayable");
    }

    function revertView() public view {
        revert("RevertReasonView");
    }

    function revertPure() public pure {
        revert("RevertReasonPure");
    }

    function revertWithNothing() public {
        revert();
    }

    function revertWithString() public {
        require(false, "Some revert message");
    }

    function revertWithCustomError() public {
        revert SomeCustomError();
    }

    function revertWithPanic() public {
        uint z = 100;
        uint y = 0;
        uint x = z / y;
    }

    function revertWithNothingPure() pure public {
        revert();
    }

    function revertWithStringPure() pure public {
        require(false, "Some revert message");
    }

    function revertWithCustomErrorPure() pure public {
        revert SomeCustomError();
    }

    function revertWithPanicPure() pure public {
        uint z = 100;
        uint y = 0;
        uint x = z / y;
    }
}
