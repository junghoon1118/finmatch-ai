package com.finmatch.finmatch_ai;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class BankExcelReadExample {

    public static void main(String[] args) throws Exception {

        Path file = Path.of("data", "bank_transactions.xlsx");

        BankExcelReader reader = new BankExcelReader();

        BankTransactionService service =
                new BankTransactionService(reader);

        try (InputStream input = Files.newInputStream(file)) {

            List<BankTransaction> transactions =
                    service.readTransactions(input);

            List<BankTransaction> withdrawals =
                    service.extractWithdrawals(transactions);

            List<BankTransaction> eventExpenses =
                    service.extractEventExpenses(transactions);

            List<BankTransaction> carryovers =
                    service.extractCarryoverWithdrawals(transactions);

            System.out.println("파일: " + file.toAbsolutePath());
            System.out.println("전체 거래: "
                    + transactions.size() + "건");
            System.out.println("전체 출금: "
                    + withdrawals.size() + "건");
            System.out.println("행사 지출: "
                    + eventExpenses.size() + "건");
            System.out.println("이월 출금: "
                    + carryovers.size() + "건");

            System.out.println("\n[연도별 전체 출금]");

            service.summarizeWithdrawalsByYear(transactions)
                    .forEach((year, summary) ->
                            System.out.printf(
                                    "%d년: %,d건 / %,d원%n",
                                    year,
                                    summary.count(),
                                    summary.totalAmount()
                            )
                    );

            System.out.println("\n[출금 거래 예시: 처음 5건]");

            withdrawals.stream()
                    .limit(5)
                    .forEach(System.out::println);
        }
    }
}