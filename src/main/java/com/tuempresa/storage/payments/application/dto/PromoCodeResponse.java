package com.tuempresa.storage.payments.application.dto;

import java.math.BigDecimal;

public record PromoCodeResponse(
        boolean valid,
        String code,
        String description,
        String discountType,
        BigDecimal discountValue,
        BigDecimal calculatedDiscount,
        String message) {

    public static PromoCodeResponse valid(String code, String description, String discountType,
            BigDecimal discountValue, BigDecimal calculatedDiscount) {
        return new PromoCodeResponse(true, code, description, discountType, discountValue, calculatedDiscount,
                "Codigo promocional aplicado.");
    }

    public static PromoCodeResponse invalid(String message) {
        return new PromoCodeResponse(false, null, null, null, null, null, message);
    }
}
