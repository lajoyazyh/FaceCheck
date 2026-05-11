package com.facecheck.checkin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.facecheck.auth.filter.JwtAuthenticationFilter;
import com.facecheck.checkin.api.dto.MyAttendanceRecordResponse;
import com.facecheck.checkin.model.AttendanceRecordStatus;
import com.facecheck.checkin.service.MyAttendanceRecordQueryService;
import com.facecheck.common.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MyAttendanceRecordController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MyAttendanceRecordControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyAttendanceRecordQueryService myAttendanceRecordQueryService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void shouldExposeSelfOnlyAttendanceHistoryWithoutAdminOnlyFields() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        given(myAttendanceRecordQueryService.listCurrentUserRecords(any(), any(), any()))
                .willReturn(List.of(new MyAttendanceRecordResponse(
                        recordId,
                        sessionId,
                        "Morning Roll Call",
                        Instant.parse("2026-05-11T08:30:00Z"),
                        AttendanceRecordStatus.VALID,
                        "Check-in completed successfully."
                )));

        mockMvc.perform(get("/api/me/attendance-records")
                        .param("from", "2026-05-10T00:00:00Z")
                        .param("to", "2026-05-12T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].recordId").value(recordId.toString()))
                .andExpect(jsonPath("$.data[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data[0].sessionName").value("Morning Roll Call"))
                .andExpect(jsonPath("$.data[0].checkinTime").value("2026-05-11T08:30:00Z"))
                .andExpect(jsonPath("$.data[0].status").value("VALID"))
                .andExpect(jsonPath("$.data[0].message").value("Check-in completed successfully."))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data[0].maskedUsername").doesNotExist())
                .andExpect(jsonPath("$.data[0].similarity").doesNotExist());
    }
}
