package com.shreeya.vehicletitle.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RequestFingerprint {

    public String calculate(SubmitTitleCommand command) {
        OwnerCommand owner = command.owner();
        String canonical = String.join("|",
                command.vin(),
                command.jurisdiction(),
                command.transactionType().name(),
                owner.fullName(),
                owner.email(),
                owner.addressLine1(),
                owner.city(),
                owner.state(),
                owner.postalCode(),
                normalizePrice(command.purchasePrice()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String normalizePrice(BigDecimal price) {
        return price == null ? "" : price.stripTrailingZeros().toPlainString();
    }
}
