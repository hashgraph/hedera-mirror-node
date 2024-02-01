@contractbase @fullsuite @equivalence @acceptance
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
  and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I verify the equivalence contract bytecode is deployed
    Then I verify the selfdestruct contract bytecode is deployed
    Then I execute selfdestruct and set beneficiary to <account> address
    Then I execute balance opcode to system account <account> address would return 0
    Then I verify extcodesize opcode against a system account <account> address returns 0
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes
    Examples:
      | account    |
      | "0.0.1"    |
      | "0.0.10"   |
      | "0.0.358"  |
      | "0.0.750"  |
      | "0.0.999"  |

#   Separated scenario as it needs to be executed only once
    Scenario Outline: Validate in-equivalence for selfdestruct and balance against contract
      Given I successfully create selfdestruct contract
      Then I verify the selfdestruct contract bytecode is deployed
      Given I successfully create equivalence call contract
      Then I verify the equivalence contract bytecode is deployed
      Then the mirror node REST API should return status 200 for the contracts creation
      Then I execute balance opcode against a contract with balance
      Then I execute selfdestruct and set beneficiary to the deleted contract address

  Scenario Outline: Validate in-equivalence tests for system accounts with call, staticcall, delegatecall
  and callcode
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I verify the equivalence contract bytecode is deployed
    Then I make internal <callType> to system account <account> <amountType> amount to <node> node

    Examples:
      | callType       | account   | amountType | node     |
      | "call"         | "0.0.0"   | "without"  | "MIRROR" |
      | "call"         | "0.0.0"   | "with"     | "MIRROR" |
      | "call"         | "0.0.9"   | "without"  | "MIRROR" |
      | "call"         | "0.0.9"   | "with"     | "MIRROR" |
      | "call"         | "0.0.357" | "without"  | "MIRROR" |
      | "call"         | "0.0.357" | "with"     | "MIRROR" |
      | "call"         | "0.0.358" | "without"  | "MIRROR" |
      | "call"         | "0.0.358" | "with"     | "MIRROR" |
      | "call"         | "0.0.741" | "without"  | "MIRROR" |
      | "call"         | "0.0.741" | "with"     | "MIRROR" |
      | "call"         | "0.0.800" | "without"  | "MIRROR" |
      | "call"         | "0.0.800" | "with"     | "MIRROR" |
      | "staticcall"   | "0.0.0"   | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.9"   | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.357" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.358" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.741" | "without"  | "MIRROR" |
      | "staticcall"   | "0.0.800" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.0"   | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.9"   | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.357" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.358" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.741" | "without"  | "MIRROR" |
      | "delegatecall" | "0.0.800" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.0"   | "without"  | "MIRROR" |
      | "callcode"     | "0.0.0"   | "with"     | "MIRROR" |
      | "callcode"     | "0.0.9"   | "without"  | "MIRROR" |
      | "callcode"     | "0.0.9"   | "with"     | "MIRROR" |
      | "callcode"     | "0.0.357" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.357" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.358" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.358" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.741" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.741" | "with"     | "MIRROR" |
      | "callcode"     | "0.0.800" | "without"  | "MIRROR" |
      | "callcode"     | "0.0.800" | "with"     | "MIRROR" |