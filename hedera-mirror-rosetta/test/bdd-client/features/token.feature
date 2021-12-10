@hts
Feature: HTS related transactions

  Scenario Outline: Validate complete token flow
    The scenario validates the following token transactions in order: create, associate, freeze & unfreeze, grant kyc,
    mint & burn, transfer, wipe account, revoke kyc, delete, and dissociate
    When I create a <tokenType> token
    Then the DATA API should show the TokenCreate transaction
    When The user associate with the token
    Then the DATA API should show the TokenAssociate transaction
    When I freeze the transfer of the token for the user
    Then the DATA API should show the TokenFreezeAccount transaction
    When I unfreeze the transfer of the token for the user
    Then the DATA API should show the TokenUnfreezeAccount transaction
    When I grant kyc to the user for the token
    Then the DATA API should show the TokenGrantKyc transaction
    When I mint token
    Then the DATA API should show the TokenMint transaction
    When I burn token
    Then the DATA API should show the TokenBurn transaction
    When I transfer some token from the treasury to the user
    Then the DATA API should show the CryptoTransfer transaction with token transfers
    When I wipe some token from the user
    Then the DATA API should show the TokenWipeAccount transaction
    When I revoke kyc from the user for the token
    Then the DATA API should show the TokenRevokeKyc transaction
    When I update the token
    Then the DATA API should show the TokenUpdate transaction
    When I delete the token
    Then the DATA API should show the TokenDelete transaction
    When The user dissociate with the token
    Then the DATA API should show the TokenDissociate transaction
    Examples:
      | tokenType           |
      | FUNGIBLE_COMMON     |
      | NON_FUNGIBLE_UNIQUE |
