@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
    and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute balance opcode against a contract with balance
    Then I execute selfdestruct and set beneficiary to <account> address with call to <node>
    Then I execute balance opcode to system account <account> address would return 0 with call to <node>
    Then I verify extcodesize opcode against a system account <account> address returns 0 with call to <node>
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes with call to <node>
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes with call to <node>
    Given I successfully create selfdestruct contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute selfdestruct and set beneficiary to the deleted contract address
    Examples:
      | account   | node        |
      | "0.0.0"   | "consensus" |
      | "0.0.0"   | "mirror"    |
      | "0.0.1"   | "consensus" |
      | "0.0.1"   | "mirror"    |
      | "0.0.2"   | "consensus" |
      | "0.0.2"   | "mirror"    |
      | "0.0.3"   | "consensus" |
      | "0.0.3"   | "mirror"    |
      | "0.0.4"   | "consensus" |
      | "0.0.4"   | "mirror"    |
      | "0.0.5"   | "consensus" |
      | "0.0.5"   | "mirror"    |
      | "0.0.6"   | "consensus" |
      | "0.0.6"   | "mirror"    |
      | "0.0.7"   | "consensus" |
      | "0.0.7"   | "mirror"    |
      | "0.0.8"   | "consensus" |
      | "0.0.8"   | "mirror"    |
      | "0.0.9"   | "consensus" |
      | "0.0.9"   | "mirror"    |
      | "0.0.10"  | "consensus" |
      | "0.0.10"  | "mirror"    |
      | "0.0.11"  | "consensus" |
      | "0.0.11"  | "mirror"    |
      | "0.0.356" | "consensus" |
      | "0.0.356" | "mirror"    |
      | "0.0.357" | "consensus" |
      | "0.0.357" | "mirror"    |
      | "0.0.358" | "consensus" |
      | "0.0.358" | "mirror"    |
      | "0.0.359" | "consensus" |
      | "0.0.359" | "mirror"    |
      | "0.0.360" | "consensus" |
      | "0.0.360" | "mirror"    |
      | "0.0.361" | "consensus" |
      | "0.0.361" | "mirror"    |
      | "0.0.750" | "consensus" |
      | "0.0.750" | "mirror"    |
      | "0.0.999" | "consensus" |
      | "0.0.999" | "mirror"    |
