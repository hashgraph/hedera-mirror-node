@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance @update
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractUpdate
        Given I successfully create a contract from <contractName> contract bytes
        When the network confirms contract presence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully update the contract
        And the network confirms contract update
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode | contractName  |
            | 200            | "MIRROR_NODE" |

    @critical @release @acceptance @contract
    Scenario Outline: Validate Contract Call - ContractCreate and ContractCall
        Given I successfully create a contract from <contractName> contract bytes
        When the network confirms contract presence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        Examples:
            | httpStatusCode | contractName  |
            | 200            | "PARENT"      |
            | 200            | "MIRROR_NODE" |

    @critical @release @acceptance @delete
    Scenario Outline: Validate Contract Flow - ContractCreate and ContractDelete
        Given I successfully create a contract from <contractName> contract bytes
        When the network confirms contract presence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully delete the contract
        And the network confirms contract absence
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Examples:
            | httpStatusCode | contractName |
            | 200            | "PARENT"     |
