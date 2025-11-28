package com.asp.parsing.detection;

import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultBankDetector hybrid detection algorithm.
 */
@ExtendWith(MockitoExtension.class)
class DefaultBankDetectorTest {

    @Mock
    private BankConnectionRepository bankConnectionRepository;

    @InjectMocks
    private DefaultBankDetector bankDetector;

    private UUID testBankConnectionId;
    private BankConnection testBankConnection;
    private Bank testBank;

    @BeforeEach
    void setUp() {
        testBankConnectionId = UUID.randomUUID();
        testBank = Bank.builder()
            .id(UUID.randomUUID())
            .name("CIH Bank")
            .slug("cih")
            .country("MA")
            .build();
        
        testBankConnection = BankConnection.builder()
            .id(testBankConnectionId)
            .bank(testBank)
            .build();
    }

    @Test
    void testStrongCIHDetection_HighConfidence() {
        // Arrange: PDF text with multiple CIH mentions
        String extractedText = """
            CIH - Crédit Immobilier et Hôtelier
            Statement from CIH Bank
            CIH Account Statement
            Crédit Immobilier et Hôtelier
            """;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier());
        assertEquals(BankDetectionResult.DetectionConfidence.HIGH, result.getConfidence());
        assertEquals("pattern_matching", result.getMetadata().get("source"));
        assertTrue((Integer) result.getMetadata().get("matchCount") >= 3);
        assertNotNull(result.getMatchedPatterns());
        assertFalse(result.getMatchedPatterns().isEmpty());
        
        // Verify fallback was NOT called (HIGH confidence should return early)
        verify(bankConnectionRepository, never()).findById(any());
    }

    @Test
    void testWeakDetection_FallsBackToConnection() {
        // Arrange: PDF text with only 1 CIH mention (LOW confidence)
        String extractedText = "CIH Account Statement";
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection));

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        assertEquals("bank_connection_fallback", result.getMetadata().get("source"));
        
        // Verify pattern results are in metadata
        assertNotNull(result.getMetadata().get("pattern_bestBank"));
        assertNotNull(result.getMetadata().get("pattern_confidence"));
        assertNotNull(result.getMetadata().get("pattern_matchCount"));
        assertNotNull(result.getMetadata().get("pattern_matchesByBank"));
        
        // Verify fallback was called
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testMismatchCase_PatternDetectsAttijari_ConnectionIsCIH() {
        // Arrange: PDF text with 2 Attijari mentions (MEDIUM confidence), but connection is CIH
        // This will trigger fallback, and mismatch flag should be set
        String extractedText = "AttijariWafa Bank\nAttijari Statement"; // 2 matches = MEDIUM
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection)); // CIH connection

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback (connection)
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        
        // Verify mismatch flag is set
        assertTrue((Boolean) result.getMetadata().get("connection_mismatch"));
        assertEquals("attijari", result.getMetadata().get("pattern_bestBank"));
        assertEquals("MEDIUM", result.getMetadata().get("pattern_confidence"));
        assertEquals(2, result.getMetadata().get("pattern_matchCount"));
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testNoPatternMatches_UsesFallback() {
        // Arrange: PDF text with no bank identifiers
        String extractedText = "Generic Bank Statement\nAccount Number: 123456";
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection));

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        assertEquals("bank_connection_fallback", result.getMetadata().get("source"));
        assertNull(result.getMetadata().get("pattern_bestBank")); // No pattern matches
        assertEquals("NONE", result.getMetadata().get("pattern_confidence"));
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testEmptyText_UsesFallback() {
        // Arrange
        String extractedText = "";
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection));

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        assertEquals("bank_connection_fallback", result.getMetadata().get("source"));
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testNullText_UsesFallback() {
        // Arrange
        String extractedText = null;
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection));

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testBPDetection() {
        // Arrange: PDF text with Banque Populaire mentions
        String extractedText = """
            Banque Populaire du Maroc
            Groupe Banque Populaire
            BP Banque Populaire
            BP Statement
            """;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("bp", result.getBankIdentifier());
        assertEquals(BankDetectionResult.DetectionConfidence.HIGH, result.getConfidence());
        assertEquals("pattern_matching", result.getMetadata().get("source"));
        assertTrue((Integer) result.getMetadata().get("matchCount") >= 3);
    }

    @Test
    void testAWBDetection_AsAttijariAlias() {
        // Arrange: PDF text with AWB mentions (should match Attijari pattern)
        String extractedText = """
            AWB Bank Statement
            AWB Account
            AWB Transaction
            """;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("attijari", result.getBankIdentifier()); // AWB matches Attijari pattern
        assertEquals(BankDetectionResult.DetectionConfidence.HIGH, result.getConfidence());
        assertEquals("pattern_matching", result.getMetadata().get("source"));
    }

    @Test
    void testBMCEDetection() {
        // Arrange: PDF text with BMCE mentions
        String extractedText = """
            BMCE Bank
            Banque Marocaine du Commerce Extérieur
            BMCE Statement
            """;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("bmce", result.getBankIdentifier());
        assertEquals(BankDetectionResult.DetectionConfidence.HIGH, result.getConfidence());
        assertEquals("pattern_matching", result.getMetadata().get("source"));
    }

    @Test
    void testMultipleBanks_SelectsHighestMatch() {
        // Arrange: PDF text with multiple banks, but CIH has more matches
        String extractedText = """
            CIH Bank
            CIH Account
            CIH Statement
            Attijari Bank
            BMCE Bank
            """;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // CIH has 3 matches, others have 1
        assertEquals(BankDetectionResult.DetectionConfidence.HIGH, result.getConfidence());
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> matchesByBank = (Map<String, Integer>) result.getMetadata().get("matchesByBank");
        assertNotNull(matchesByBank);
        assertEquals(3, matchesByBank.get("cih"));
        assertEquals(1, matchesByBank.get("attijari"));
        assertEquals(1, matchesByBank.get("bmce"));
    }

    @Test
    void testFallback_NoConnectionId() {
        // Arrange
        String extractedText = "Generic text with no bank identifiers";
        UUID nullConnectionId = null;

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, nullConnectionId);

        // Assert
        assertNotNull(result);
        assertNull(result.getBankIdentifier());
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        assertEquals("fallback_no_connection", result.getMetadata().get("source"));
        
        verify(bankConnectionRepository, never()).findById(any());
    }

    @Test
    void testFallback_ConnectionNotFound() {
        // Arrange
        String extractedText = "Generic text";
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.empty());

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertNull(result.getBankIdentifier());
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        assertEquals("fallback_not_found", result.getMetadata().get("source"));
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }

    @Test
    void testMismatchCase_Detailed() {
        // Arrange: Pattern detects Attijari (MEDIUM confidence), connection is CIH
        String extractedText = "AttijariWafa Bank\nAttijari Statement"; // 2 matches = MEDIUM
        
        when(bankConnectionRepository.findById(testBankConnectionId))
            .thenReturn(Optional.of(testBankConnection)); // CIH connection

        // Act
        BankDetectionResult result = bankDetector.detect(extractedText, testBankConnectionId);

        // Assert
        assertNotNull(result);
        assertEquals("cih", result.getBankIdentifier()); // From fallback (connection)
        assertEquals(BankDetectionResult.DetectionConfidence.LOW, result.getConfidence());
        
        // Verify mismatch flag
        assertTrue((Boolean) result.getMetadata().get("connection_mismatch"));
        assertEquals("attijari", result.getMetadata().get("pattern_bestBank"));
        assertEquals("MEDIUM", result.getMetadata().get("pattern_confidence"));
        assertEquals(2, result.getMetadata().get("pattern_matchCount"));
        
        // Verify pattern metadata is preserved
        @SuppressWarnings("unchecked")
        Map<String, Integer> matchesByBank = (Map<String, Integer>) result.getMetadata().get("pattern_matchesByBank");
        assertNotNull(matchesByBank);
        assertTrue(matchesByBank.containsKey("attijari"));
        
        verify(bankConnectionRepository, times(1)).findById(testBankConnectionId);
    }
}

