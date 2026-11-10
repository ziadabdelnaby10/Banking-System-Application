package org.bank.customer.service;

import org.bank.customer.config.CustomerProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class NationalIdFingerprintTest {

    private final NationalIdFingerprint fingerprints =
            new NationalIdFingerprint(new CustomerProperties("unit-test-pepper-0123456789"));

    @Test
    void normalizationIgnoresCaseSpacesAndHyphens() {
        assertEquals("A123456", NationalIdFingerprint.normalize(" a-123 456 "));
    }

    @Test
    void sameIdAlwaysGivesTheSameFingerprintAndNeverContainsTheId() {
        String normalized = NationalIdFingerprint.normalize("29001011234567");

        String first = fingerprints.fingerprint(normalized);

        assertAll(
                () -> assertEquals(first, fingerprints.fingerprint(normalized)),
                () -> assertEquals(64, first.length()),
                () -> assertFalse(first.contains("1234567")));
    }

    @Test
    void differentIdsOrDifferentPeppersGiveDifferentFingerprints() {
        NationalIdFingerprint otherPepper = new NationalIdFingerprint(new CustomerProperties("another-pepper-0123456789"));

        assertAll(
                () -> assertNotEquals(fingerprints.fingerprint("A1"), fingerprints.fingerprint("A2")),
                () -> assertNotEquals(fingerprints.fingerprint("A1"), otherPepper.fingerprint("A1")));
    }

    @Test
    void lastCharactersHandlesShortValues() {
        assertAll(
                () -> assertEquals("4567", NationalIdFingerprint.lastCharacters("29001011234567")),
                () -> assertEquals("AB", NationalIdFingerprint.lastCharacters("AB")));
    }
}
