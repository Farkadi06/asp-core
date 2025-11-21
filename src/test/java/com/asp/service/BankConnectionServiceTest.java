package com.asp.service;

import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import com.asp.domain.BankRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import com.asp.dto.BankConnectionResponse;
import com.asp.domain.BankConnectionNextStepType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankConnectionService Unit Tests")
class BankConnectionServiceTest {

    @Mock
    private BankConnectionRepository bankConnectionRepository;

    @Mock
    private BankRepository bankRepository;

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private BankConnectionService bankConnectionService;

    private UUID testTenantId;
    private UUID testBankId;
    private UUID testConnectionId;
    private String testUserRef;
    private Tenant testTenant;
    private Bank testBank;
    private BankConnection testConnection;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testBankId = UUID.randomUUID();
        testConnectionId = UUID.randomUUID();
        testUserRef = "sara_001";

        // Set public base URL for tests
        ReflectionTestUtils.setField(bankConnectionService, "publicBaseUrl", "http://localhost:8080");

        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .plan(Tenant.TenantPlan.PRO)
                .build();

        testBank = Bank.builder()
                .id(testBankId)
                .name("Test Bank")
                .slug("test-bank")
                .country("MA")
                .connectionType(Bank.ConnectionType.OAUTH)
                .status(Bank.BankStatus.ACTIVE)
                .build();

        testConnection = BankConnection.builder()
                .id(testConnectionId)
                .tenant(testTenant)
                .bank(testBank)
                .status(BankConnection.ConnectionStatus.WAITING_OAUTH)
                .userRef(testUserRef)
                .metadata(new HashMap<>())
                .build();
    }

    @Test
    @DisplayName("createConnection - Success: Store correct userRef")
    void testCreateConnection_StoresCorrectUserRef() {
        // Arrange
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(bankRepository.findById(testBankId))
                .thenReturn(Optional.of(testBank));
        when(bankConnectionRepository.save(any(BankConnection.class)))
                .thenReturn(testConnection);

        // Act
        BankConnectionResponse response = bankConnectionService.createConnection(
                testBankId, testTenantId, testUserRef);

        // Assert
        assertNotNull(response);
        assertEquals(testConnectionId, response.getConnectionId());
        assertEquals(testBankId, response.getBankId());
        assertEquals(testUserRef, response.getUserRef());
        assertEquals("WAITING_OAUTH", response.getStatus());

        verify(tenantRepository, times(1)).findById(testTenantId);
        verify(bankRepository, times(1)).findById(testBankId);
        verify(bankConnectionRepository, times(1)).save(argThat(connection ->
                connection.getUserRef().equals(testUserRef) &&
                connection.getTenant().getId().equals(testTenantId) &&
                connection.getBank().getId().equals(testBankId)
        ));
    }

    @Test
    @DisplayName("createConnection - Exception: Missing userRef")
    void testCreateConnection_MissingUserRef() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bankConnectionService.createConnection(testBankId, testTenantId, null)
        );

        assertTrue(exception.getMessage().contains("userRef cannot be null or empty"));
        verify(bankConnectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConnection - Exception: Empty userRef")
    void testCreateConnection_EmptyUserRef() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bankConnectionService.createConnection(testBankId, testTenantId, "")
        );

        assertTrue(exception.getMessage().contains("userRef cannot be null or empty"));
        verify(bankConnectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConnection - Success: Trims userRef whitespace")
    void testCreateConnection_TrimsUserRef() {
        // Arrange
        String userRefWithWhitespace = "  sara_001  ";
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(bankRepository.findById(testBankId))
                .thenReturn(Optional.of(testBank));
        when(bankConnectionRepository.save(any(BankConnection.class)))
                .thenAnswer(invocation -> {
                    BankConnection connection = invocation.getArgument(0);
                    return BankConnection.builder()
                            .id(testConnectionId)
                            .tenant(connection.getTenant())
                            .bank(connection.getBank())
                            .status(connection.getStatus())
                            .userRef(connection.getUserRef())
                            .build();
                });

        // Act
        BankConnectionResponse response = bankConnectionService.createConnection(
                testBankId, testTenantId, userRefWithWhitespace);

        // Assert
        assertNotNull(response);
        verify(bankConnectionRepository, times(1)).save(argThat(connection ->
                connection.getUserRef().equals("sara_001")
        ));
    }

    @Test
    @DisplayName("listConnections - Success: Filter by userRef")
    void testListConnections_FilteredByUserRef() {
        // Arrange
        when(bankConnectionRepository.findAllByTenantIdAndUserRef(testTenantId, testUserRef))
                .thenReturn(Collections.singletonList(testConnection));

        // Act
        List<BankConnectionResponse> responses = bankConnectionService.listConnections(testTenantId, testUserRef);

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(testUserRef, responses.get(0).getUserRef());
        assertEquals(testConnectionId, responses.get(0).getConnectionId());

        verify(bankConnectionRepository, times(1)).findAllByTenantIdAndUserRef(testTenantId, testUserRef);
        verify(bankConnectionRepository, never()).findAllByTenantId(any());
    }

    @Test
    @DisplayName("listConnections - Success: Return all when no userRef filter")
    void testListConnections_AllWhenNoFilter() {
        // Arrange
        BankConnection connection2 = BankConnection.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .bank(testBank)
                .status(BankConnection.ConnectionStatus.PENDING)
                .userRef("ali_002")
                .build();

        when(bankConnectionRepository.findAllByTenantId(testTenantId))
                .thenReturn(Arrays.asList(testConnection, connection2));

        // Act
        List<BankConnectionResponse> responses = bankConnectionService.listConnections(testTenantId, null);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());

        verify(bankConnectionRepository, times(1)).findAllByTenantId(testTenantId);
        verify(bankConnectionRepository, never()).findAllByTenantIdAndUserRef(any(), any());
    }

    @Test
    @DisplayName("listConnections - Success: Empty userRef treated as no filter")
    void testListConnections_EmptyUserRefTreatedAsNoFilter() {
        // Arrange
        when(bankConnectionRepository.findAllByTenantId(testTenantId))
                .thenReturn(Collections.singletonList(testConnection));

        // Act
        List<BankConnectionResponse> responses = bankConnectionService.listConnections(testTenantId, "");

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());

        verify(bankConnectionRepository, times(1)).findAllByTenantId(testTenantId);
        verify(bankConnectionRepository, never()).findAllByTenantIdAndUserRef(any(), any());
    }

    @Test
    @DisplayName("getConnection - Success: Returns userRef in response")
    void testGetConnection_ReturnsUserRef() {
        // Arrange
        when(bankConnectionRepository.findByIdAndTenantId(testConnectionId, testTenantId))
                .thenReturn(Optional.of(testConnection));

        // Act
        BankConnectionResponse response = bankConnectionService.getConnection(testConnectionId, testTenantId);

        // Assert
        assertNotNull(response);
        assertEquals(testConnectionId, response.getConnectionId());
        assertEquals(testUserRef, response.getUserRef());
        assertEquals("WAITING_OAUTH", response.getStatus());

        verify(bankConnectionRepository, times(1)).findByIdAndTenantId(testConnectionId, testTenantId);
    }

    @Test
    @DisplayName("mapToResponse - Success: Always includes userRef")
    void testMapToResponse_AlwaysIncludesUserRef() {
        // Arrange
        when(bankConnectionRepository.findByIdAndTenantId(testConnectionId, testTenantId))
                .thenReturn(Optional.of(testConnection));

        // Act
        BankConnectionResponse response = bankConnectionService.getConnection(testConnectionId, testTenantId);

        // Assert
        assertNotNull(response.getUserRef());
        assertEquals(testUserRef, response.getUserRef());
        assertNotNull(response.getNextStep());
        assertEquals(BankConnectionNextStepType.OAUTH_REDIRECT, response.getNextStep().getType());
    }

    @Test
    @DisplayName("createConnection - Exception: Tenant not found")
    void testCreateConnection_TenantNotFound() {
        // Arrange
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bankConnectionService.createConnection(testBankId, testTenantId, testUserRef)
        );

        assertTrue(exception.getMessage().contains("Tenant not found"));
        verify(bankConnectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConnection - Exception: Bank not found")
    void testCreateConnection_BankNotFound() {
        // Arrange
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(bankRepository.findById(testBankId))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bankConnectionService.createConnection(testBankId, testTenantId, testUserRef)
        );

        assertTrue(exception.getMessage().contains("Bank not found"));
        verify(bankConnectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createConnection - Exception: Bank not ACTIVE")
    void testCreateConnection_BankNotActive() {
        // Arrange
        Bank inactiveBank = Bank.builder()
                .id(testBankId)
                .name("Inactive Bank")
                .slug("inactive-bank")
                .country("MA")
                .connectionType(Bank.ConnectionType.OAUTH)
                .status(Bank.BankStatus.INACTIVE)
                .build();

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(bankRepository.findById(testBankId))
                .thenReturn(Optional.of(inactiveBank));

        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> bankConnectionService.createConnection(testBankId, testTenantId, testUserRef)
        );

        assertTrue(exception.getMessage().contains("Bank is not ACTIVE"));
        verify(bankConnectionRepository, never()).save(any());
    }
}

