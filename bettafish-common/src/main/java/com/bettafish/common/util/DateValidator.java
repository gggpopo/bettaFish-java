package com.bettafish.common.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class DateValidator {

    private DateValidator() {
    }

    public static boolean isIsoDate(String value) {
        return parse(() -> LocalDate.parse(value));
    }

    public static boolean isIsoDateTime(String value) {
        return parse(() -> OffsetDateTime.parse(value));
    }

    private static boolean parse(Parser parser) {
        try {
            parser.parse();
            return true;
        } catch (DateTimeParseException | NullPointerException ex) {
            return false;
        }
    }

    @FunctionalInterface
    private interface Parser {
        void parse();
    }
}
