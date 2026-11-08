package com.finmatch.finmatch_ai;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.finmatch.finmatch_ai.ReconciliationResult.Status;

public class ReconciliationExample {
    public static void main(String[] args) throws Exception {
        // 1. 은행 Excel 읽기 -> 행사 지출만 추출
        BankTransactionService bankService =
                new BankTransactionService(new BankExcelReader());
        List<BankTransaction> bankExpenses;
        try (InputStream in = Files.newInputStream(
                Path.of("data", "bank_transactions.xlsx"))) {
            bankExpenses = bankService.extractEventExpenses(
                    bankService.readTransactions(in));
        }

        // 2. 장부 Excel 읽기
        List<ExpenseLedgerEntry> ledger;
        try (InputStream in = Files.newInputStream(
                Path.of("data", "student_council_ledger.xlsx"))) {
            ledger = new LedgerExcelReader().read(in);
        }

        // 3. 비교
        List<ReconciliationResult> results =
                new ReconciliationService().reconcile(bankExpenses, ledger);

        System.out.println("은행 행사 지출: " + bankExpenses.size() + "건, 장부: " + ledger.size() + "건");

        // 4. 판정별 건수
        Map<Status, Integer> counts = new EnumMap<>(Status.class);
        for (ReconciliationResult r : results) {
            counts.merge(r.status(), 1, Integer::sum);
        }
        counts.forEach((status, count) ->
                System.out.println(status.label() + ": " + count + "건"));

        // 5. 일치가 아닌 항목만 자세히 출력
        System.out.println("---- 확인이 필요한 항목 ----");
        for (ReconciliationResult r : results) {
            if (r.status() == Status.MATCHED) {
                continue;
            }
            String bank = r.bankTransaction() == null
                    ? "(은행 없음)" : r.bankTransaction().transactionId();
            String ledgers = r.ledgerEntries().stream()
                    .map(ExpenseLedgerEntry::ledgerId)
                    .toList().toString();
            System.out.println("[" + r.status().label() + "] 은행 " + bank
                    + " / 장부 " + ledgers);
        }
    }
}