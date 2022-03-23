@cryptoallowance @allowance @fullsuite
Feature: Account Crypto Allowance Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate approval CryptoTransfer
        Given I approve <senderName> to transfer up to <approvedAmount> tℏ
        Then the mirror node REST API should confirm the approved <approvedAmount> tℏ crypto transfer allowance
        When <senderName> transfers <transferAmount> tℏ from their approved balance to <recipientName>
        Then the mirror node REST API should return status <httpStatusCode> for the crypto transfer transaction
        Given I adjust <senderName> transfer allowance to <updateApprovedAmount> tℏ
        Then the mirror node REST API should confirm the approved <updateApprovedAmount> tℏ crypto transfer allowance
        When I delete the crypto allowance for <senderName>
        Then the mirror node REST API should confirm the crypto allowance deletion
        Examples:
            | senderName | approvedAmount | recipientName | transferAmount | httpStatusCode | updateApprovedAmount |
            | "ALICE"    | 10000          | "BOB"         | 2500           | 200            | 5000                 |

    @critical @release @acceptance
    Scenario: Validate allowance cleanup
        Given I remove all my allowances from my account
        Then the mirror node REST API should confirm no granted allowances remain
