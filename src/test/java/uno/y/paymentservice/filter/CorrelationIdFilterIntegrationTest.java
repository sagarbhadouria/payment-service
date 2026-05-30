package uno.y.paymentservice.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = TestController.class)
@ActiveProfiles("test")
@Import(CorrelationIdFilter.class)
class CorrelationIdFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldGenerateCorrelationIdWhenHeaderNotProvided() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/correlation-id"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String headerCorrelationId = result.getResponse().getHeader("X-Correlation-Id");

        assertThat(responseBody).isNotNull().isNotEmpty();
        assertThat(responseBody).startsWith("req-");
        assertThat(responseBody).isEqualTo(headerCorrelationId);
    }

    @Test
    void shouldUseProvidedCorrelationIdFromHeader() throws Exception {
        String providedCorrelationId = "my-custom-trace-123";

        mockMvc.perform(get("/test/correlation-id")
                        .header("X-Correlation-Id", providedCorrelationId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", providedCorrelationId))
                .andExpect(content().string(providedCorrelationId));
    }

    @Test
    void shouldGenerateNewCorrelationIdWhenHeaderIsEmpty() throws Exception {
        String emptyHeader = "";

        MvcResult result = mockMvc.perform(get("/test/correlation-id")
                        .header("X-Correlation-Id", emptyHeader))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String headerCorrelationId = result.getResponse().getHeader("X-Correlation-Id");

        assertThat(responseBody).startsWith("req-");
        assertThat(headerCorrelationId).startsWith("req-");
        assertThat(responseBody).isEqualTo(headerCorrelationId);
    }

    @Test
    void shouldGenerateUniqueCorrelationIdsForDifferentRequests() throws Exception {
        MvcResult result1 = mockMvc.perform(get("/test/correlation-id"))
                .andReturn();
        String firstId = result1.getResponse().getContentAsString();

        MvcResult result2 = mockMvc.perform(get("/test/correlation-id"))
                .andReturn();
        String secondId = result2.getResponse().getContentAsString();

        assertThat(firstId).isNotEqualTo(secondId);
    }
}