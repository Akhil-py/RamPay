package com.rampay.paymentservice.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CurrencyValidator.
 * Tests currency code validation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CurrencyValidator Tests")
class CurrencyValidatorTest {

    private CurrencyValidator currencyValidator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        currencyValidator = new CurrencyValidator();
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code USD")
    void testIsValid_ValidUSD() {
        assertTrue(currencyValidator.isValid("USD", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code EUR")
    void testIsValid_ValidEUR() {
        assertTrue(currencyValidator.isValid("EUR", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code GBP")
    void testIsValid_ValidGBP() {
        assertTrue(currencyValidator.isValid("GBP", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code JPY")
    void testIsValid_ValidJPY() {
        assertTrue(currencyValidator.isValid("JPY", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code CAD")
    void testIsValid_ValidCAD() {
        assertTrue(currencyValidator.isValid("CAD", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code AUD")
    void testIsValid_ValidAUD() {
        assertTrue(currencyValidator.isValid("AUD", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code CHF")
    void testIsValid_ValidCHF() {
        assertTrue(currencyValidator.isValid("CHF", context));
    }

    @Test
    @DisplayName("isValid - Should return true for valid currency code CNY")
    void testIsValid_ValidCNY() {
        assertTrue(currencyValidator.isValid("CNY", context));
    }

    @Test
    @DisplayName("isValid - Should return false for invalid currency code")
    void testIsValid_InvalidCurrency() {
        assertFalse(currencyValidator.isValid("XYZ", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with lowercase letters")
    void testIsValid_LowercaseCurrency() {
        assertFalse(currencyValidator.isValid("usd", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with mixed case")
    void testIsValid_MixedCaseCurrency() {
        assertFalse(currencyValidator.isValid("UsD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with numbers")
    void testIsValid_CurrencyWithNumbers() {
        assertFalse(currencyValidator.isValid("US1", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with special characters")
    void testIsValid_CurrencyWithSpecialChars() {
        assertFalse(currencyValidator.isValid("US$", context));
    }

    @Test
    @DisplayName("isValid - Should return false for empty string")
    void testIsValid_EmptyString() {
        assertFalse(currencyValidator.isValid("", context));
    }

    @Test
    @DisplayName("isValid - Should return true for null value")
    void testIsValid_NullValue() {
        assertTrue(currencyValidator.isValid(null, context));
    }

    @Test
    @DisplayName("isValid - Should return false for single letter")
    void testIsValid_SingleLetter() {
        assertFalse(currencyValidator.isValid("U", context));
    }

    @Test
    @DisplayName("isValid - Should return false for two letters")
    void testIsValid_TwoLetters() {
        assertFalse(currencyValidator.isValid("US", context));
    }

    @Test
    @DisplayName("isValid - Should return false for four letters")
    void testIsValid_FourLetters() {
        assertFalse(currencyValidator.isValid("USDD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with spaces")
    void testIsValid_CurrencyWithSpaces() {
        assertFalse(currencyValidator.isValid("US D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with leading/trailing spaces")
    void testIsValid_CurrencyWithLeadingTrailingSpaces() {
        assertFalse(currencyValidator.isValid(" USD ", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with hyphen")
    void testIsValid_CurrencyWithHyphen() {
        assertFalse(currencyValidator.isValid("US-D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with underscore")
    void testIsValid_CurrencyWithUnderscore() {
        assertFalse(currencyValidator.isValid("US_D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with dot")
    void testIsValid_CurrencyWithDot() {
        assertFalse(currencyValidator.isValid("US.D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with comma")
    void testIsValid_CurrencyWithComma() {
        assertFalse(currencyValidator.isValid("US,D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with emoji")
    void testIsValid_CurrencyWithEmoji() {
        assertFalse(currencyValidator.isValid("US💵", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with unicode characters")
    void testIsValid_CurrencyWithUnicode() {
        assertFalse(currencyValidator.isValid("US€", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with tab character")
    void testIsValid_CurrencyWithTab() {
        assertFalse(currencyValidator.isValid("US\tD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with newline character")
    void testIsValid_CurrencyWithNewline() {
        assertFalse(currencyValidator.isValid("US\nD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with carriage return")
    void testIsValid_CurrencyWithCarriageReturn() {
        assertFalse(currencyValidator.isValid("US\rD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with form feed")
    void testIsValid_CurrencyWithFormFeed() {
        assertFalse(currencyValidator.isValid("US\fD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with backspace")
    void testIsValid_CurrencyWithBackspace() {
        assertFalse(currencyValidator.isValid("US\bD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with zero-width space")
    void testIsValid_CurrencyWithZeroWidthSpace() {
        assertFalse(currencyValidator.isValid("US\u200BD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with non-breaking space")
    void testIsValid_CurrencyWithNonBreakingSpace() {
        assertFalse(currencyValidator.isValid("US\u00A0D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with soft hyphen")
    void testIsValid_CurrencyWithSoftHyphen() {
        assertFalse(currencyValidator.isValid("US\u00ADD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with zero-width joiner")
    void testIsValid_CurrencyWithZeroWidthJoiner() {
        assertFalse(currencyValidator.isValid("US\u200DD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with zero-width non-joiner")
    void testIsValid_CurrencyWithZeroWidthNonJoiner() {
        assertFalse(currencyValidator.isValid("US\u200CD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with left-to-right mark")
    void testIsValid_CurrencyWithLeftToRightMark() {
        assertFalse(currencyValidator.isValid("US\u200ED", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with right-to-left mark")
    void testIsValid_CurrencyWithRightToLeftMark() {
        assertFalse(currencyValidator.isValid("US\u200FD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with multiple spaces")
    void testIsValid_CurrencyWithMultipleSpaces() {
        assertFalse(currencyValidator.isValid("U S D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with only spaces")
    void testIsValid_OnlySpaces() {
        assertFalse(currencyValidator.isValid("   ", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with punctuation")
    void testIsValid_CurrencyWithPunctuation() {
        assertFalse(currencyValidator.isValid("US!D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with brackets")
    void testIsValid_CurrencyWithBrackets() {
        assertFalse(currencyValidator.isValid("US(D)", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with braces")
    void testIsValid_CurrencyWithBraces() {
        assertFalse(currencyValidator.isValid("US{D}", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with angle brackets")
    void testIsValid_CurrencyWithAngleBrackets() {
        assertFalse(currencyValidator.isValid("US<D>", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with square brackets")
    void testIsValid_CurrencyWithSquareBrackets() {
        assertFalse(currencyValidator.isValid("US[D]", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with pipe character")
    void testIsValid_CurrencyWithPipe() {
        assertFalse(currencyValidator.isValid("US|D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with backslash")
    void testIsValid_CurrencyWithBackslash() {
        assertFalse(currencyValidator.isValid("US\\D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with forward slash")
    void testIsValid_CurrencyWithForwardSlash() {
        assertFalse(currencyValidator.isValid("US/D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with at sign")
    void testIsValid_CurrencyWithAtSign() {
        assertFalse(currencyValidator.isValid("US@D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with hash sign")
    void testIsValid_CurrencyWithHashSign() {
        assertFalse(currencyValidator.isValid("US#D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with percent sign")
    void testIsValid_CurrencyWithPercentSign() {
        assertFalse(currencyValidator.isValid("US%D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with ampersand")
    void testIsValid_CurrencyWithAmpersand() {
        assertFalse(currencyValidator.isValid("US&D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with asterisk")
    void testIsValid_CurrencyWithAsterisk() {
        assertFalse(currencyValidator.isValid("US*D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with plus sign")
    void testIsValid_CurrencyWithPlusSign() {
        assertFalse(currencyValidator.isValid("US+D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with equals sign")
    void testIsValid_CurrencyWithEqualsSign() {
        assertFalse(currencyValidator.isValid("US=D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with question mark")
    void testIsValid_CurrencyWithQuestionMark() {
        assertFalse(currencyValidator.isValid("US?D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with exclamation mark")
    void testIsValid_CurrencyWithExclamationMark() {
        assertFalse(currencyValidator.isValid("US!D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with tilde")
    void testIsValid_CurrencyWithTilde() {
        assertFalse(currencyValidator.isValid("US~D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with grave accent")
    void testIsValid_CurrencyWithGraveAccent() {
        assertFalse(currencyValidator.isValid("US`D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with single quote")
    void testIsValid_CurrencyWithSingleQuote() {
        assertFalse(currencyValidator.isValid("US'D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with double quote")
    void testIsValid_CurrencyWithDoubleQuote() {
        assertFalse(currencyValidator.isValid("US\"D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with semicolon")
    void testIsValid_CurrencyWithSemicolon() {
        assertFalse(currencyValidator.isValid("US;D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with colon")
    void testIsValid_CurrencyWithColon() {
        assertFalse(currencyValidator.isValid("US:D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with less than sign")
    void testIsValid_CurrencyWithLessThanSign() {
        assertFalse(currencyValidator.isValid("US<D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with greater than sign")
    void testIsValid_CurrencyWithGreaterThanSign() {
        assertFalse(currencyValidator.isValid("US>D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with caret")
    void testIsValid_CurrencyWithCaret() {
        assertFalse(currencyValidator.isValid("US^D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with tilde character")
    void testIsValid_CurrencyWithTildeCharacter() {
        assertFalse(currencyValidator.isValid("US~D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with backtick")
    void testIsValid_CurrencyWithBacktick() {
        assertFalse(currencyValidator.isValid("US`D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with vertical bar")
    void testIsValid_CurrencyWithVerticalBar() {
        assertFalse(currencyValidator.isValid("US|D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with multiple special characters")
    void testIsValid_CurrencyWithMultipleSpecialChars() {
        assertFalse(currencyValidator.isValid("US$%^D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with mixed case and special chars")
    void testIsValid_CurrencyWithMixedCaseAndSpecialChars() {
        assertFalse(currencyValidator.isValid("Us$D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with leading special character")
    void testIsValid_CurrencyWithLeadingSpecialChar() {
        assertFalse(currencyValidator.isValid("$USD", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with trailing special character")
    void testIsValid_CurrencyWithTrailingSpecialChar() {
        assertFalse(currencyValidator.isValid("USD$", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with middle special character")
    void testIsValid_CurrencyWithMiddleSpecialChar() {
        assertFalse(currencyValidator.isValid("US$D", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with repeated characters")
    void testIsValid_CurrencyWithRepeatedChars() {
        assertFalse(currencyValidator.isValid("UUU", context));
    }

    @Test
    @DisplayName("isValid - Should return false for currency code with all same characters")
    void testIsValid_CurrencyWithAllSameChars() {
        assertFalse(currencyValidator.isValid("SSS", context));
    }
}
