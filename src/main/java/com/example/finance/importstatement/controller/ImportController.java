package com.example.finance.importstatement.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.finance.importstatement.model.ImportBatch;
import com.example.finance.importstatement.service.ImportService;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/gpay", consumes = "multipart/form-data")
    public ImportBatch importGpay(@RequestPart("file") MultipartFile file) {
        return importService.importFile(file);
    }
}
