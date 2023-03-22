@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Contract Flow - ContractCreate, ContractUpdate, ContractCall

        Using the Java SDK and the mirror node REST API, create, update and call a smart contract and
        verify proper operation.

        Given I successfully create a contract from the parent contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully update the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the updated contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the called contract function
        And I call the contract via the mirror node REST API
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 10000000       |

    @critical @release @acceptance
    Scenario Outline: Validate EVM address, CREATE2

        Utilizing the parent contract deployed in the scenario above, invoke its functions used to determine
        the EVM address of supplied (child) contract bytecode and salt and then deploy that contract
        using the EVM CREATE2 opcode.

        Clean up the parent contract by deleting it and its bytecode file.

        Then I call the parent contract evm address function with the bytecode of the child contract
        And I create a hollow account using CryptoTransfer of <transferAmount> to the evm address
        Then the mirror node REST API should return status <httpStatusCode> for the account transaction
        Then the mirror node REST API should verify the account receiving <transferAmount> is hollow
        And the mirror node REST API should indicate not found when using evm address to retrieve as a contract
#        Then I deploy a child contract by calling a function on the parent contract to deploy to the evm address using CREATE2
#        And now the mirror node REST API should return status <httpStatusCode> when using evm address to retrieve the child contract
#        Then I successfully delete the child contract by calling it and causing it to self destruct

        Examples:
            | httpStatusCode | transferAmount |
            | 200            | 1000           |

    @critical @release @acceptance
    Scenario Outline: Clean up parent contract, ContractDelete, FileDelete

        Clean up the parent contract by deleting it and its bytecode file.

        When I successfully delete the parent contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Then I successfully delete the parent contract bytecode file
        Examples:
            | httpStatusCode |
            | 200            |
