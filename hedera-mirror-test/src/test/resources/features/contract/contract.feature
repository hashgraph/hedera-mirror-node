@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance @update
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractUpdate
        Given I successfully create a contract from contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully update the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 1000           |

    @critical @release @acceptance
    Scenario Outline: Validate Contract Call - ContractCreate and ContractCall
        Given I successfully create a contract from contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 0              |

    @critical @release @acceptance @delete
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractDelete
        Given I successfully create a contract from contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully delete the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 0              |
