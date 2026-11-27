package de.omegazirkel.risingworld.marketplace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class MarketplaceServiceWantedListingTest {
    @Test
    public void failedReservationAndRemovalNeverMoveOrdinaryBalances() {
        RecordingWallet wallet = new RecordingWallet();
        MarketplaceService service = new MarketplaceService(null, wallet, null, null);
        assertTrue(service.rollbackWantedPayout(listing(), 9, 25, "COIN", "", false, false));

        assertEquals(List.of(), wallet.calls);
    }

    @Test
    public void onlyTheRequesterDebitIsRefundedWhenSellerCreditDidNotComplete() {
        RecordingWallet wallet = new RecordingWallet();
        MarketplaceService service = new MarketplaceService(null, wallet, null, null);
        assertTrue(service.rollbackWantedPayout(listing(), 9, 25, "COIN", "", true, false));
        assertEquals(List.of("deposit 7 25"), wallet.calls);
    }

    @Test
    public void failedSellerReversalCannotCreateAnUnbackedRefund() {
        RecordingWallet wallet = new RecordingWallet();
        wallet.failWithdrawal = true;
        MarketplaceService service = new MarketplaceService(null, wallet, null, null);
        assertFalse(service.rollbackWantedPayout(listing(), 9, 25, "COIN", "", true, true));
        assertEquals(List.of("withdraw 9 25"), wallet.calls);
    }

    @Test
    public void successfulOrdinaryAndCrierReversalsUseTheirOwnPaymentLegs() {
        RecordingWallet wallet = new RecordingWallet();
        MarketplaceService service = new MarketplaceService(null, wallet, null, null);
        assertTrue(service.rollbackWantedPayout(listing(), 9, 25, "COIN", "", true, true));
        assertTrue(service.rollbackWantedPayout(listing(), 9, 25, "COIN", "crier-payout", false, false));
        assertEquals(List.of("withdraw 9 25", "deposit 7 25", "reverse crier-payout crier-payout:refund"), wallet.calls);
    }

    private static MarketplaceListing listing() {
        return new MarketplaceListing(42, 7, "Requester", "stone", 0, 1,
                MarketplaceItemState.NEUTRAL, 25, "COIN", "global", true, 1, "ACTIVE");
    }

    private static final class RecordingWallet extends WalletBridge {
        final List<String> calls = new ArrayList<>();
        boolean failWithdrawal;

        RecordingWallet() {
            super(null);
        }

        @Override
        public WalletCallResult withdraw(int player, long amount, String reason, String currency, String plugin) {
            calls.add("withdraw " + player + " " + amount);
            return new WalletCallResult(!failWithdrawal, "");
        }

        @Override
        public WalletTransferCallResult reverseAccountTransferIdempotent(String original, String reversal,
                String reason, String plugin) {
            calls.add("reverse " + original + " " + reversal);
            return new WalletTransferCallResult(true, "", "");
        }

        @Override
        public WalletCallResult deposit(int player, long amount, String reason, String currency, String plugin) {
            calls.add("deposit " + player + " " + amount);
            return new WalletCallResult(true, "");
        }
    }

    @Test
    public void globalCrierWantedListingsUseTheGlobalEndpoint() {
        MarketZone crierZone = new MarketZone("crier-14", "Global Crier", 0L,
                0, 0, 0, 0, 0, 0, 0, MarketZone.GLOBAL_ALLOW, 1L, 0, "", "");
        MarketCrier crier = new MarketCrier(14L, "crier-14", "Global Crier", 0, "", true,
                true, false, 1, true, 1L);

        assertEquals("global", MarketplaceService.wantedListingEndpoint(Optional.of(crierZone), crier));
    }

    @Test
    public void personalCrierWantedListingsKeepTheirCrierEndpoint() {
        MarketZone crierZone = new MarketZone("crier-15", "Personal Crier", 0L,
                0, 0, 0, 0, 0, 0, 0, MarketZone.GLOBAL_DENY, 1L, 7, "Owner", "");
        MarketCrier crier = new MarketCrier(15L, "crier-15", "Personal Crier", 7, "Owner", false,
                false, false, 1, true, 1L);

        assertEquals("crier-15", MarketplaceService.wantedListingEndpoint(Optional.of(crierZone), crier));
    }
}
