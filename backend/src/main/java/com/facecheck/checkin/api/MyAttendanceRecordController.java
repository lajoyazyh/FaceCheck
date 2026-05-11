package com.facecheck.checkin.api;

import com.facecheck.checkin.api.dto.MyAttendanceRecordResponse;
import com.facecheck.checkin.service.MyAttendanceRecordQueryService;
import com.facecheck.common.api.ApiResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/attendance-records")
public class MyAttendanceRecordController {

    private final MyAttendanceRecordQueryService myAttendanceRecordQueryService;

    public MyAttendanceRecordController(MyAttendanceRecordQueryService myAttendanceRecordQueryService) {
        this.myAttendanceRecordQueryService = myAttendanceRecordQueryService;
    }

    @GetMapping
    public ApiResponse<List<MyAttendanceRecordResponse>> listCurrentUserRecords(
            Authentication authentication,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ApiResponse.success(myAttendanceRecordQueryService.listCurrentUserRecords(authentication, from, to));
    }
}
