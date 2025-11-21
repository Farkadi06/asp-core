package com.asp.api;

import com.asp.dto.BankConnectionNextStep;
import com.asp.dto.BankConnectionResponse;
import com.asp.dto.CreateBankConnectionRequest;
import com.asp.domain.BankConnectionNextStepType;
import com.asp.service.BankConnectionService;
import com.asp.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BankConnectionController.class)
@DisplayName("BankConnectionController Unit Tests")
class BankConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BankConnectionService bankConnectionService;

    private UUID testTenantId;
    private UUID testBankId;
    private UUID testConnectionId;
    private String testUserRef;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testBankId = UUID.randomUUID();
        testConnectionId = UUID.randomUUID();
        testUserRef = "sara_001";
        TenantContext.setTenantId(testTenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /v1/bank-connections/{bankId}/connect - Success: Create connection with userRef")
    @WithMockUser
    void testCreateConnection_Success() throws Exception {
        // Arrange
        CreateBankConnectionRequest request = CreateBankConnectionRequest.builder()
                .userRef(testUserRef)
                .build();

        BankConnectionNextStep nextStep = BankConnectionNextStep.builder()
                .type(BankConnectionNextStepType.OAUTH_REDIRECT)
                .details(Map.of("redirectUrl", "http://localhost:8080/v1/bank-connections/" + testConnectionId + "/oauth/start"))
                .build();

        BankConnectionResponse response = BankConnectionResponse.builder()
                .connectionId(testConnectionId)
                .bankId(testBankId)
                .status("WAITING_OAUTH")
                .userRef(testUserRef)
                .nextStep(nextStep)
                .metadata(new HashMap<>())
                .build();

        when(bankConnectionService.createConnection(eq(testBankId), eq(testTenantId), eq(testUserRef)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/v1/bank-connections/{bankId}/connect", testBankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.connectionId").value(testConnectionId.toString()))
                .andExpect(jsonPath("$.bankId").value(testBankId.toString()))
                .andExpect(jsonPath("$.status").value("WAITING_OAUTH"))
                .andExpect(jsonPath("$.userRef").value(testUserRef))
                .andExpect(jsonPath("$.nextStep.type").value("OAUTH_REDIRECT"))
                .andExpect(jsonPath("$.nextStep.details.redirectUrl").exists());

        verify(bankConnectionService, times(1)).createConnection(eq(testBankId), eq(testTenantId), eq(testUserRef));
    }

    @Test
    @DisplayName("POST /v1/bank-connections/{bankId}/connect - Bad Request: Missing userRef")
    @WithMockUser
    void testCreateConnection_MissingUserRef() throws Exception {
        // Arrange
        CreateBankConnectionRequest request = CreateBankConnectionRequest.builder()
                .userRef(null)
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/bank-connections/{bankId}/connect", testBankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(bankConnectionService, never()).createConnection(any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/bank-connections/{bankId}/connect - Bad Request: Empty userRef")
    @WithMockUser
    void testCreateConnection_EmptyUserRef() throws Exception {
        // Arrange
        CreateBankConnectionRequest request = CreateBankConnectionRequest.builder()
                .userRef("")
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/bank-connections/{bankId}/connect", testBankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(bankConnectionService, never()).createConnection(any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/bank-connections/{bankId}/connect - Bad Request: Blank userRef")
    @WithMockUser
    void testCreateConnection_BlankUserRef() throws Exception {
        // Arrange
        CreateBankConnectionRequest request = CreateBankConnectionRequest.builder()
                .userRef("   ")
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/bank-connections/{bankId}/connect", testBankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(bankConnectionService, never()).createConnection(any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/bank-connections/{bankId}/connect - Unauthorized: No tenant context")
    @WithMockUser
    void testCreateConnection_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();
        CreateBankConnectionRequest request = CreateBankConnectionRequest.builder()
                .userRef(testUserRef)
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/bank-connections/{bankId}/connect", testBankId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(bankConnectionService, never()).createConnection(any(), any(), any());
    }

    @Test
    @DisplayName("GET /v1/bank-connections - Success: List all connections for tenant")
    @WithMockUser
    void testListConnections_All() throws Exception {
        // Arrange
        BankConnectionResponse connection1 = BankConnectionResponse.builder()
                .connectionId(testConnectionId)
                .bankId(testBankId)
                .status("PENDING")
                .userRef("sara_001")
                .build();

        BankConnectionResponse connection2 = BankConnectionResponse.builder()
                .connectionId(UUID.randomUUID())
                .bankId(UUID.randomUUID())
                .status("CONNECTED")
                .userRef("ali_002")
                .build();

        when(bankConnectionService.listConnections(eq(testTenantId), eq(null)))
                .thenReturn(Arrays.asList(connection1, connection2));

        // Act & Assert
        mockMvc.perform(get("/v1/bank-connections"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].connectionId").value(testConnectionId.toString()))
                .andExpect(jsonPath("$[0].userRef").value("sara_001"))
                .andExpect(jsonPath("$[1].userRef").value("ali_002"));

        verify(bankConnectionService, times(1)).listConnections(eq(testTenantId), eq(null));
    }

    @Test
    @DisplayName("GET /v1/bank-connections?userRef=sara_001 - Success: Filter by userRef")
    @WithMockUser
    void testListConnections_FilteredByUserRef() throws Exception {
        // Arrange
        BankConnectionResponse connection = BankConnectionResponse.builder()
                .connectionId(testConnectionId)
                .bankId(testBankId)
                .status("PENDING")
                .userRef(testUserRef)
                .build();

        when(bankConnectionService.listConnections(eq(testTenantId), eq(testUserRef)))
                .thenReturn(Collections.singletonList(connection));

        // Act & Assert
        mockMvc.perform(get("/v1/bank-connections")
                        .param("userRef", testUserRef))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].connectionId").value(testConnectionId.toString()))
                .andExpect(jsonPath("$[0].userRef").value(testUserRef));

        verify(bankConnectionService, times(1)).listConnections(eq(testTenantId), eq(testUserRef));
    }

    @Test
    @DisplayName("GET /v1/bank-connections/{connectionId} - Success: Get connection with userRef")
    @WithMockUser
    void testGetConnection_Success() throws Exception {
        // Arrange
        BankConnectionResponse response = BankConnectionResponse.builder()
                .connectionId(testConnectionId)
                .bankId(testBankId)
                .status("PENDING")
                .userRef(testUserRef)
                .build();

        when(bankConnectionService.getConnection(eq(testConnectionId), eq(testTenantId)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/v1/bank-connections/{connectionId}", testConnectionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionId").value(testConnectionId.toString()))
                .andExpect(jsonPath("$.bankId").value(testBankId.toString()))
                .andExpect(jsonPath("$.userRef").value(testUserRef));

        verify(bankConnectionService, times(1)).getConnection(eq(testConnectionId), eq(testTenantId));
    }

    @Test
    @DisplayName("GET /v1/bank-connections/{connectionId} - Unauthorized: No tenant context")
    @WithMockUser
    void testGetConnection_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        mockMvc.perform(get("/v1/bank-connections/{connectionId}", testConnectionId))
                .andExpect(status().isUnauthorized());

        verify(bankConnectionService, never()).getConnection(any(), any());
    }

    @Test
    @DisplayName("GET /v1/bank-connections - Unauthorized: No tenant context")
    @WithMockUser
    void testListConnections_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        mockMvc.perform(get("/v1/bank-connections"))
                .andExpect(status().isUnauthorized());

        verify(bankConnectionService, never()).listConnections(any(), any());
    }
}

