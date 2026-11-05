package com.finmatch.finmatch_ai;
// 웹에서 업로드한 은행/장부 Excel을 받아 비교 결과를 JSON으로 돌려주는 클래스

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ExcelUploadController {

    private final BankTransactionService bankTransactionService;
    private final LedgerExcelReader ledgerExcelReader;
    private final ReconciliationService reconciliationService;

    public ExcelUploadController(
            BankTransactionService bankTransactionService,
            LedgerExcelReader ledgerExcelReader,
            ReconciliationService reconciliationService) {
        this.bankTransactionService = bankTransactionService;
        this.ledgerExcelReader = ledgerExcelReader;
        this.reconciliationService = reconciliationService;
    }

    // POST /api/reconciliation
    // 은행 Excel(bankFile)과 장부 Excel(ledgerFile)을 받아 비교한다.
    @PostMapping("/reconciliation")
    public ResponseEntity<?> reconcile(
            @RequestParam("bankFile") MultipartFile bankFile,
            @RequestParam("ledgerFile") MultipartFile ledgerFile) {

        // 1. 파일 검사: 비어 있거나 .xlsx가 아니면 돌려보낸다
        String fileError = checkFile(bankFile, "은행 거래내역");
        if (fileError == null) {
            fileError = checkFile(ledgerFile, "학생회 장부");
        }
        if (fileError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", fileError));
        }

        try {
            // 2. 은행 Excel 읽기 -> 행사 지출만 추출
            List<BankTransaction> bankExpenses;
            try (InputStream in = bankFile.getInputStream()) {
                bankExpenses = bankTransactionService.extractEventExpenses(
                        bankTransactionService.readTransactions(in));
            }

            // 3. 장부 Excel 읽기
            List<ExpenseLedgerEntry> ledgerEntries;
            try (InputStream in = ledgerFile.getInputStream()) {
                ledgerEntries = ledgerExcelReader.read(in);
            }

            // 4. 비교
            List<ReconciliationResult> results =
                    reconciliationService.reconcile(bankExpenses, ledgerEntries);

            return ResponseEntity.ok(toResponse(bankExpenses.size(),
                    ledgerEntries.size(), results));

        } catch (IllegalArgumentException e) {
            // Reader가 "시트, 행, 열" 정보를 담아 던진 오류: 파일 내용이 잘못된 경우
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            // 열 수 없는 파일 (.xlsx 확장자인데 Excel이 아닌 경우 등)
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Excel 파일을 읽을 수 없습니다. 올바른 .xlsx 파일인지 확인해 주세요."));
        }
    }

    // 파일 한 개를 검사해서 문제가 있으면 오류 문구를, 없으면 null을 돌려준다
    private String checkFile(MultipartFile file, String label) {
        if (file.isEmpty()) {
            return label + " 파일이 비어 있습니다.";
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            return label + " 파일은 .xlsx 형식이어야 합니다.";
        }
        return null;
    }

    // 비교 결과를 화면에 보여 주기 쉬운 형태로 바꾼다
    private ReconciliationResponse toResponse(int bankCount, int ledgerCount,
            List<ReconciliationResult> results) {

        // 판정별 건수 (판정 이름 -> 건수)
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (ReconciliationResult.Status status
                : ReconciliationResult.Status.values()) {
            summary.put(status.label(), 0);
        }
        for (ReconciliationResult result : results) {
            summary.merge(result.status().label(), 1, Integer::sum);
        }
        // 0건인 판정은 목록에서 뺀다
        summary.entrySet().removeIf(entry -> entry.getValue() == 0);

        List<ResultItem> items = new ArrayList<>();
        for (ReconciliationResult result : results) {
            BankTransaction bank = result.bankTransaction();
            List<ExpenseLedgerEntry> ledgers = result.ledgerEntries();

            items.add(new ResultItem(
                    result.status().label(),
                    bank == null ? null : bank.transactionId(),
                    bank == null ? null : bank.transactionDateTime().toString(),
                    bank == null ? null : bank.counterparty(),
                    bank == null ? null : bank.amount(),
                    ledgers.stream().map(ExpenseLedgerEntry::ledgerId).toList(),
                    ledgers.isEmpty() ? null : ledgers.get(0).counterparty(),
                    ledgers.isEmpty() ? null : ledgers.get(0).amount(),
                    result.reason()));
        }

        return new ReconciliationResponse(bankCount, ledgerCount, summary, items);
    }

    // 화면으로 보내는 전체 응답
    public record ReconciliationResponse(
            int bankExpenseCount,
            int ledgerCount,
            Map<String, Integer> summary,
            List<ResultItem> items) {
    }

    // 결과 한 줄 (은행 쪽 값과 장부 쪽 값을 나란히 담는다)
    public record ResultItem(
            String status,
            String bankId,
            String bankDateTime,
            String bankCounterparty,
            Long bankAmount,
            List<String> ledgerIds,
            String ledgerCounterparty,
            Long ledgerAmount,
            String reason) {
    }
}
