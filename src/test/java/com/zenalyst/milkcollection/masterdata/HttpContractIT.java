package com.zenalyst.milkcollection.masterdata;

import com.zenalyst.milkcollection.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Protocol-level behaviour. A catch-all {@code @ExceptionHandler(Exception.class)} will happily
 * turn an unknown URL or a wrong method into a 500 unless the framework's own exceptions are
 * handled explicitly, so these cases are pinned down.
 */
class HttpContractIT extends IntegrationTestBase {

    @Test
    @DisplayName("an unknown path is 404, not 500")
    void unknownPathIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("a wrong HTTP method is 405, not 500")
    void wrongMethodIsMethodNotAllowed() throws Exception {
        mockMvc.perform(delete("/api/v1/villages"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("an unsupported content type is 415, not 500")
    void wrongContentTypeIsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/villages")
                        .contentType("text/plain")
                        .content("code=V-001"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("malformed JSON is 400 and never leaks parser internals")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/villages").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request could not be parsed"));
    }

    @Test
    @DisplayName("a non-numeric path variable is 400, not 500")
    void badPathVariableTypeIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/villages/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("an Accept header this API cannot satisfy is 406, not 500")
    void unsatisfiableAcceptIsNotAcceptable() throws Exception {
        // The status carries the whole message here: the client asked for a representation this
        // API cannot produce, so sending a JSON body anyway would contradict the 406.
        mockMvc.perform(get("/api/v1/villages").accept("application/xml"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("an unparseable enum in a query parameter is 400, not 500")
    void badEnumQueryParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/runs").param("shift", "AFTERNOON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void healthIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
