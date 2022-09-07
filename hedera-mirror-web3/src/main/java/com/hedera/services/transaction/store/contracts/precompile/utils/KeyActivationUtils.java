package com.hedera.services.transaction.store.contracts.precompile.utils;

import static com.hedera.services.transaction.store.contracts.WorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;

import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

import com.hedera.mirror.web3.evm.SimulatedAliasManager;
import com.hedera.mirror.web3.repository.TokenRepository;

public final class KeyActivationUtils {

    private KeyActivationUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Checks if a key implicit in a target address is active in the current frame using a {@link
     * KeyActivationTest}.
     *
     * <p>We massage the current frame a bit to ensure that a precompile being executed via delegate
     * call is tested as such. There are three cases.
     *
     * <ol>
     *   <li>The precompile is being executed via a delegate call, so the current frame's
     *       <b>recipient</b> (not sender) is really the "active" contract that can match a {@code
     *       delegatable_contract_id} key; or,
     *   <li>The precompile is being executed via a call, but the calling code was executed via a
     *       delegate call, so although the current frame's sender <b>is</b> the "active" contract,
     *       it must be evaluated using an activation test that restricts to {@code
     *       delegatable_contract_id} keys; or,
     *   <li>The precompile is being executed via a call, and the calling code is being executed as
     *       part of a non-delegate call.
     * </ol>
     *
     * <p>Note that because the {@link DecodingFacade} converts every address to its "mirror"
     * address form (as needed for e.g. the {@link TransferLogic} implementation), we can assume the
     * target address is a mirror address. All other addresses we resolve to their mirror form
     * before proceeding.
     *
     * @param frame current frame
     * @param target the element to test for key activation, in standard form
     * @param activationTest the function which should be invoked for key validation
     * @param tokenRepository token repository
     * @param aliases the current Hedera contract aliases
     * @return whether the implied key is active
     */
    public static boolean validateKey(
            final MessageFrame frame,
            final Address target,
            final KeyActivationTest activationTest,
            final TokenRepository tokenRepository,
            final SimulatedAliasManager aliases) {
        final var recipient = aliases.resolveForEvm(frame.getRecipientAddress());
        final var sender = aliases.resolveForEvm(frame.getSenderAddress());

        if (isDelegateCall(frame) && !isToken(frame, recipient)) {
            return activationTest.apply(true, target, recipient, aliases, tokenRepository);
        } else {
            final var parentFrame = getParentOf(frame);
            final var delegated = parentFrame.map(KeyActivationUtils::isDelegateCall).orElse(false);
            return activationTest.apply(delegated, target, sender, aliases, tokenRepository);
        }
    }

    static boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }

    private static Optional<MessageFrame> getParentOf(final MessageFrame frame) {
        final var it = frame.getMessageFrameStack().descendingIterator();

        if (it.hasNext()) {
            it.next();
        } else {
            return Optional.empty();
        }

        MessageFrame parentFrame;
        if (it.hasNext()) {
            parentFrame = it.next();
        } else {
            return Optional.empty();
        }

        return Optional.of(parentFrame);
    }

    private static boolean isDelegateCall(final MessageFrame frame) {
        final var contract = frame.getContractAddress();
        final var recipient = frame.getRecipientAddress();
        return !contract.equals(recipient);
    }
}
