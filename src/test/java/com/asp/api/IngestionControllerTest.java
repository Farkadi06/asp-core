package com.asp.api;

import com.asp.dto.IngestionResponse;
import com.asp.service.IngestionService;
import com.asp.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
@DisplayName("IngestionController Unit Tests")
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;


    @MockBean
    private IngestionService ingestionService;

    private UUID testTenantId;
    private UUID testBankConnectionId;
    private UUID testIngestionId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testBankConnectionId = UUID.randomUUID();
        testIngestionId = UUID.randomUUID();
        TenantContext.setTenantId(testTenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /v1/ingestions - Success: Upload PDF file")
    @WithMockUser
    void testCreateIngestion_Success() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-statement.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.4 test content".getBytes()
        );

        IngestionResponse response = IngestionResponse.builder()
            .id(testIngestionId)
            .bankConnectionId(testBankConnectionId)
            .userRef("user_sara_001")
            .status("PENDING")
            .source("MANUAL_UPLOAD")
            .ingestionType("PDF_STATEMENT")
            .originalFileName("test-statement.pdf")
            .fileSize(20L)
            .storagePath("tenants/" + testTenantId + "/ingestions/" + testIngestionId + "/original/test-statement.pdf")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ingestionService.createIngestionForPdf(eq(testTenantId), eq(testBankConnectionId), any()))
            .thenReturn(response);

        // Act & Assert
        mockMvc.perform(multipart("/v1/ingestions")
                .file(file)
                .param("bankConnectionId", testBankConnectionId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(testIngestionId.toString()))
            .andExpect(jsonPath("$.bankConnectionId").value(testBankConnectionId.toString()))
            .andExpect(jsonPath("$.userRef").value("user_sara_001"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.ingestionType").value("PDF_STATEMENT"))
            .andExpect(jsonPath("$.originalFileName").value("test-statement.pdf"))
            .andExpect(jsonPath("$.fileSize").value(20L));

        verify(ingestionService, times(1))
            .createIngestionForPdf(eq(testTenantId), eq(testBankConnectionId), any());
    }

    @Test
    @DisplayName("POST /v1/ingestions - Bad Request: Missing file")
    @WithMockUser
    void testCreateIngestion_MissingFile() throws Exception {
        // Arrange - Mock service to throw exception for null file
        when(ingestionService.createIngestionForPdf(any(), any(), any()))
            .thenThrow(new IllegalArgumentException("File cannot be null or empty"));

        // Act & Assert - Send request without file part
        // Service validation will throw IllegalArgumentException for null/empty file
        mockMvc.perform(multipart("/v1/ingestions")
                .param("bankConnectionId", testBankConnectionId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().is5xxServerError());

        verify(ingestionService, times(1))
            .createIngestionForPdf(eq(testTenantId), eq(testBankConnectionId), any());
    }

    @Test
    @DisplayName("POST /v1/ingestions - Bad Request: Missing bankConnectionId")
    @WithMockUser
    void testCreateIngestion_MissingBankConnectionId() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-statement.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.4 test content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/v1/ingestions")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isBadRequest());

        verify(ingestionService, never()).createIngestionForPdf(any(), any(), any());
    }

    @Test
    @DisplayName("POST /v1/ingestions - Unauthorized: Tenant context not set")
    @WithMockUser
    void testCreateIngestion_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "test-statement.pdf",
            MediaType.APPLICATION_PDF_VALUE,
            "%PDF-1.4 test content".getBytes()
        );

        // Act & Assert
        mockMvc.perform(multipart("/v1/ingestions")
                .file(file)
                .param("bankConnectionId", testBankConnectionId.toString())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isUnauthorized());

        verify(ingestionService, never()).createIngestionForPdf(any(), any(), any());
    }

    @Test
    @DisplayName("GET /v1/ingestions/{id} - Success: Get ingestion")
    @WithMockUser
    void testGetIngestion_Success() throws Exception {
        // Arrange
        IngestionResponse response = IngestionResponse.builder()
            .id(testIngestionId)
            .bankConnectionId(testBankConnectionId)
            .userRef("user_sara_001")
            .status("PENDING")
            .source("MANUAL_UPLOAD")
            .ingestionType("PDF_STATEMENT")
            .originalFileName("test-statement.pdf")
            .fileSize(20L)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ingestionService.getIngestion(eq(testTenantId), eq(testIngestionId)))
            .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/v1/ingestions/{id}", testIngestionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testIngestionId.toString()))
            .andExpect(jsonPath("$.bankConnectionId").value(testBankConnectionId.toString()))
            .andExpect(jsonPath("$.status").value("PENDING"));

        verify(ingestionService, times(1)).getIngestion(eq(testTenantId), eq(testIngestionId));
    }

    @Test
    @DisplayName("GET /v1/ingestions - Success: List all ingestions")
    @WithMockUser
    void testListIngestions_All() throws Exception {
        // Arrange
        IngestionResponse response1 = IngestionResponse.builder()
            .id(testIngestionId)
            .bankConnectionId(testBankConnectionId)
            .userRef("user_sara_001")
            .status("PENDING")
            .build();

        IngestionResponse response2 = IngestionResponse.builder()
            .id(UUID.randomUUID())
            .bankConnectionId(testBankConnectionId)
            .userRef("user_sara_002")
            .status("COMPLETED")
            .build();

        when(ingestionService.listIngestions(eq(testTenantId), eq(null), eq(null)))
            .thenReturn(List.of(response1, response2));

        // Act & Assert
        mockMvc.perform(get("/v1/ingestions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").value(testIngestionId.toString()))
            .andExpect(jsonPath("$[1].userRef").value("user_sara_002"));

        verify(ingestionService, times(1)).listIngestions(eq(testTenantId), eq(null), eq(null));
    }

    @Test
    @DisplayName("GET /v1/ingestions - Success: Filter by userRef")
    @WithMockUser
    void testListIngestions_FilteredByUserRef() throws Exception {
        // Arrange
        String filterUserRef = "user_sara_001";
        IngestionResponse response = IngestionResponse.builder()
            .id(testIngestionId)
            .bankConnectionId(testBankConnectionId)
            .userRef(filterUserRef)
            .status("PENDING")
            .build();

        when(ingestionService.listIngestions(eq(testTenantId), eq(filterUserRef), eq(null)))
            .thenReturn(List.of(response));

        // Act & Assert
        mockMvc.perform(get("/v1/ingestions")
                .param("userRef", filterUserRef))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].userRef").value(filterUserRef));

        verify(ingestionService, times(1)).listIngestions(eq(testTenantId), eq(filterUserRef), eq(null));
    }

    @Test
    @DisplayName("GET /v1/ingestions - Unauthorized: Tenant context not set")
    @WithMockUser
    void testListIngestions_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        mockMvc.perform(get("/v1/ingestions"))
            .andExpect(status().isUnauthorized());

        verify(ingestionService, never()).listIngestions(any(), any(), any());
    }
}

