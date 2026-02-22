package org.fourz.BarterShops.sign.renderer;

import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.ItemStack;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.BarterShops.sign.factory.SignLayoutFactory;

import java.util.List;

/**
 * Renders the BOARD mode sign layout.
 *
 * BOARD is the live-trading state. The rendered output differs by perspective:
 *   - Customer view: payment options, pagination for multi-payment BARTER shops
 *   - Owner view:    offering summary, preview-mode indicator, or payment summary
 *
 * When the shop is not yet configured, a "Not configured" fallback is shown to both.
 */
public class BoardModeRenderer implements ISignModeRenderer {

    @Override
    public void render(SignSide side, BarterSign barterSign, boolean isCustomerView) {
        if (!barterSign.isConfigured()) {
            renderNotConfigured(side, barterSign);
            return;
        }

        org.bukkit.Bukkit.getLogger().fine(String.format(
            "[BarterShops-DEBUG] displayBoardMode router: isCustomerView=%b -> %s",
            isCustomerView, isCustomerView ? "CUSTOMER" : "OWNER"
        ));

        if (isCustomerView) {
            renderCustomerPaymentPage(side, barterSign);
        } else {
            renderOwnerBoardView(side, barterSign);
        }
    }

    // -------------------------------------------------------------------------
    // Not-configured fallback
    // -------------------------------------------------------------------------

    private void renderNotConfigured(SignSide side, BarterSign barterSign) {
        side.setLine(0, SignRenderUtil.getTypeHeader(barterSign.getType()));
        side.setLine(1, "§eNot configured");
        side.setLine(2, "§eAsk owner");
        side.setLine(3, "");
    }

    // -------------------------------------------------------------------------
    // Customer-facing payment page
    // -------------------------------------------------------------------------

    /**
     * Displays customer-facing payment page for BARTER shops.
     * - Single payment: No header (barter is implicit), uses all 4 lines (0–3)
     * - Multiple payments: Header + offering + summary/paginated payment
     * - BUY/SELL: Header + offering + price
     */
    private void renderCustomerPaymentPage(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        List<ItemStack> payments = (type == SignType.BARTER) ? barterSign.getAcceptedPayments() : null;
        boolean isSinglePaymentBarter = (type == SignType.BARTER && payments != null && payments.size() == 1);

        if (!isSinglePaymentBarter) {
            side.setLine(0, SignRenderUtil.getTypeHeader(type));
        }

        int offeringStartLine = isSinglePaymentBarter ? 0 : 1;
        boolean offeringWrapped = false;
        if (offering != null) {
            offeringWrapped = SignRenderUtil.displayOfferingWithWrapping(side, offering, offeringStartLine);
        }

        if (type == SignType.BARTER) {
            renderBarterPaymentLines(side, barterSign, payments, offering, offeringWrapped);
        } else {
            renderBuySellPrice(side, barterSign, offeringWrapped);
        }
    }

    /**
     * Renders the payment lines for BARTER shops on the customer-facing view.
     */
    private void renderBarterPaymentLines(SignSide side, BarterSign barterSign,
                                          List<ItemStack> payments, ItemStack offering,
                                          boolean offeringWrapped) {
        if (payments.isEmpty()) {
            if (offeringWrapped) {
                side.setLine(3, "§eNo pay options");
            } else {
                side.setLine(2, "§eNo payment");
                side.setLine(3, "§eoptions");
            }
            return;
        }

        if (payments.size() == 1) {
            renderSinglePaymentCustomer(side, offering, payments.get(0), offeringWrapped);
            return;
        }

        // Multiple payments: summary page (index 0) or individual payment page (index 1+)
        int currentPaymentIndex = barterSign.getCurrentPaymentPage();
        if (currentPaymentIndex == 0) {
            if (offeringWrapped) {
                side.setLine(3, "§e" + payments.size() + " pay options");
            } else {
                side.setLine(2, "§e" + payments.size() + " payment");
                side.setLine(3, "§eoptions");
            }
        } else {
            renderPaginatedPayment(side, payments, currentPaymentIndex);
        }
    }

    /**
     * Single-payment BARTER: display payment below offering with wrapping support.
     */
    private void renderSinglePaymentCustomer(SignSide side, ItemStack offering,
                                             ItemStack payment, boolean offeringWrapped) {
        String paymentName = SignRenderUtil.formatItemName(payment);
        boolean paymentNeedsWrapping = paymentName != null &&
            ("for: " + payment.getAmount() + "x " + paymentName).length() > SignLayoutFactory.MAX_LINE_LENGTH;

        if (offeringWrapped && paymentNeedsWrapping) {
            SignRenderUtil.displayDualWrapMode(side, offering, payment);
        } else if (offeringWrapped) {
            side.setLine(2, "");
            side.setLine(3, "§efor: " + payment.getAmount() + "x " + SignRenderUtil.formatItemName(payment));
        } else if (paymentNeedsWrapping) {
            SignRenderUtil.displayPaymentWithWrapping(side, payment, 2);
        } else {
            side.setLine(2, "§efor: " + payment.getAmount() + "x /");
            side.setLine(3, "§e" + SignRenderUtil.formatItemName(payment));
        }
    }

    /**
     * Multi-payment BARTER pagination: shows a single payment option per page (pages 1+).
     * Page numbering: page 1 = summary, pages 2-N = individual payments.
     */
    private void renderPaginatedPayment(SignSide side, List<ItemStack> payments, int currentPaymentIndex) {
        int paymentIndex = currentPaymentIndex - 1; // 0-based payment array index
        ItemStack payment = payments.get(paymentIndex);

        int displayPageNumber = currentPaymentIndex + 1;
        int totalPages = payments.size() + 1; // 1 summary + N payments

        String itemName = SignRenderUtil.formatItemName(payment);
        side.setLine(0, "§efor " + payment.getAmount() + "x");

        if (itemName.length() > 15) {
            int splitIndex = itemName.lastIndexOf(' ', 15);
            if (splitIndex == -1) splitIndex = 15;
            side.setLine(1, "§e" + itemName.substring(0, splitIndex).trim());
            side.setLine(2, "§e" + itemName.substring(splitIndex).trim());
        } else {
            side.setLine(1, "§e" + itemName);
            side.setLine(2, "");
        }
        side.setLine(3, "§6page " + displayPageNumber + " of " + totalPages);
    }

    // -------------------------------------------------------------------------
    // Owner-facing board view
    // -------------------------------------------------------------------------

    /**
     * Displays owner-facing BOARD view.
     * Shows offering summary and either a preview-mode indicator or payment info.
     * Single-payment BARTER shops omit the header (same as customer view).
     */
    private void renderOwnerBoardView(SignSide side, BarterSign barterSign) {
        SignType type = barterSign.getType();
        ItemStack offering = barterSign.getItemOffering();

        List<ItemStack> payments = (type == SignType.BARTER) ? barterSign.getAcceptedPayments() : null;
        boolean isSinglePaymentBarter = (type == SignType.BARTER && payments != null && payments.size() == 1);

        if (!isSinglePaymentBarter) {
            side.setLine(0, SignRenderUtil.getTypeHeader(type));
        }

        int offeringStartLine = isSinglePaymentBarter ? 0 : 1;
        boolean offeringWrapped = false;
        if (offering != null) {
            offeringWrapped = SignRenderUtil.displayOfferingWithWrapping(side, offering, offeringStartLine);
        }

        if (barterSign.isOwnerPreviewMode()) {
            if (offeringWrapped) {
                side.setLine(3, "§eCustomer View");
            } else {
                side.setLine(2, "§e[Customer View]");
                side.setLine(3, "§eSneak+R: exit");
            }
        } else if (type == SignType.BARTER) {
            renderBarterOwnerLines(side, payments, offeringWrapped);
        } else {
            renderBuySellPrice(side, barterSign, offeringWrapped);
        }
    }

    /**
     * Renders BARTER payment summary lines for the owner board view.
     */
    private void renderBarterOwnerLines(SignSide side, List<ItemStack> payments, boolean offeringWrapped) {
        if (payments.size() == 1) {
            ItemStack payment = payments.get(0);
            String paymentName = SignRenderUtil.formatItemName(payment);
            boolean paymentNeedsWrapping = paymentName != null &&
                ("for: " + payment.getAmount() + "x " + paymentName).length() > SignLayoutFactory.MAX_LINE_LENGTH;

            if (offeringWrapped && paymentNeedsWrapping) {
                String paymentPrefix = "for: " + payment.getAmount() + "x ";
                int paymentSplit = SignRenderUtil.computeNameSplit(paymentName, paymentPrefix.length());
                side.setLine(2, "§e" + paymentPrefix + paymentName.substring(0, paymentSplit).trim());
                side.setLine(3, "§e" + SignLayoutFactory.truncateForSign(
                    paymentName.substring(paymentSplit).trim(), SignLayoutFactory.MAX_LINE_LENGTH));
            } else if (offeringWrapped) {
                side.setLine(2, "");
                side.setLine(3, "§efor: " + payment.getAmount() + "x " + SignRenderUtil.formatItemName(payment));
            } else {
                side.setLine(2, "§efor: " + payment.getAmount() + "x");
                side.setLine(3, "§e" + SignRenderUtil.formatItemName(payment));
            }
        } else {
            if (offeringWrapped) {
                side.setLine(3, "§e" + payments.size() + " pay options");
            } else {
                side.setLine(2, "§e" + payments.size() + " payment");
                side.setLine(3, "§eoptions");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared BUY/SELL price helper (identical layout for customer + owner)
    // -------------------------------------------------------------------------

    /**
     * Renders the BUY/SELL price lines.
     * Previously duplicated between {@code displayCustomerPaymentPage} and
     * {@code displayOwnerBoardView} — now a single method.
     */
    private void renderBuySellPrice(SignSide side, BarterSign barterSign, boolean offeringWrapped) {
        ItemStack priceItem = barterSign.getPriceItem();
        int priceAmount = barterSign.getPriceAmount();

        if (offeringWrapped) {
            side.setLine(3, "§e" + priceAmount + "x " + SignRenderUtil.formatItemName(priceItem));
        } else {
            side.setLine(2, "§e" + priceAmount + "x");
            side.setLine(3, "§e" + SignRenderUtil.formatItemName(priceItem));
        }
    }
}
