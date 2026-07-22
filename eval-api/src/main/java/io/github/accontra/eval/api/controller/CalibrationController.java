package io.github.accontra.eval.api.controller;

import io.github.accontra.eval.api.request.CalibrationRequest;
import io.github.accontra.eval.api.response.CalibrationResponse;
import io.github.accontra.eval.application.service.CalibrationService;
import io.github.accontra.eval.application.service.CalibrationService.CalibrationResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 标定 API — 将人工确认过的评估结果向量化入库。
 */
@RestController
@RequestMapping("/api/v1/calibration")
public class CalibrationController {

    private final CalibrationService calibrationService;

    public CalibrationController(CalibrationService calibrationService) {
        this.calibrationService = calibrationService;
    }

    @PostMapping("/submit")
    public CalibrationResponse submit(@RequestBody CalibrationRequest request) {
        CalibrationResult result = calibrationService.calibrate(request.getIds());
        return new CalibrationResponse(result.total(), result.success(), result.failed(), result.details());
    }
}
