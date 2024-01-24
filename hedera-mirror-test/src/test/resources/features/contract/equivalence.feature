@contractbase @fullsuite @equivalence
Feature: in-equivalence tests

  Scenario Outline: Validate in-equivalence system accounts for balance, extcodesize, extcodecopy, extcodehash
  and selfdestruct op codes
    Given I successfully create selfdestruct contract
    Given I successfully create equivalence call contract
    Then the mirror node REST API should return status 200 for the contracts creation
    Then I execute balance opcode against a contract with balance with call to <node>
    Then I execute selfdestruct and set beneficiary to <account> address with call to <node>
    Then I execute balance opcode to system account <account> address would return 0 with call to <node>
    Then I verify extcodesize opcode against a system account <account> address returns 0 with call to <node>
    Then I verify extcodecopy opcode against a system account <account> address returns empty bytes with call to <node>
    Then I verify extcodehash opcode against a system account <account> address returns empty bytes with call to <node>
    Examples:
      | account     | node        |
#      | "0.0.1"     | "CONSENSUS" |
#      | "0.0.10"    | "CONSENSUS" |
#      | "0.0.356"   | "CONSENSUS" |
#      | "0.0.357"   | "CONSENSUS" |
#      | "0.0.750"   | "CONSENSUS" |
      | "0.0.1"     | "MIRROR"    |
      | "0.0.10"    | "MIRROR"    |
      | "0.0.356"   | "MIRROR"    |
      | "0.0.357"   | "MIRROR"    |
      | "0.0.750"   | "MIRROR"    |
      | "0.0.999"   | "MIRROR"    |

#   Separated scenario as it needs to be executed only once
    Scenario Outline: Validate in-equivalence system accounts for selfdestruct
      Given I successfully create selfdestruct contract
      Then the mirror node REST API should return status 200 for the contracts creation
      Then I execute selfdestruct and set beneficiary to the deleted contract address with call to <node>
      Examples:
      |    node     |
#      | "CONSENSUS" |
      | "MIRROR"    |