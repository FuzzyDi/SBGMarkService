package uz.sbg.marking.server.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "sbg.marking.auth.enabled=true",
        "sbg.marking.auth.admin-token=admin-secret",
        "sbg.marking.auth.operator-token=operator-secret"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ApiRoleGuardFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectProtectedEndpointWithoutHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowOperatorForOperatorEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("X-SBG-Role", "OPERATOR")
                        .header("X-SBG-Token", "operator-secret"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectOperatorForAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/marks")
                        .header("X-SBG-Role", "OPERATOR")
                        .header("X-SBG-Token", "operator-secret"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAdminForAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/admin/marks")
                        .header("X-SBG-Role", "ADMIN")
                        .header("X-SBG-Token", "admin-secret"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAdminImport() throws Exception {
        mockMvc.perform(post("/api/v1/km/import/full")
                        .header("X-SBG-Role", "ADMIN")
                        .header("X-SBG-Token", "admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"batchId\":\"auth-batch\",\"items\":[]}"))
                .andExpect(status().isOk());
    }
}
