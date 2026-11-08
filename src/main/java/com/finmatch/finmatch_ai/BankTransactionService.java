package com.finmatch.finmatch_ai;
//출금 추출, 행사 지출 구분 등 거래 처리

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

@Service
public class BankTransactionService {

    private final BankExcelReader bankExcelReader;

    public BankTransactionService(
            BankExcelReader bankExcelReader) {
        this.bankExcelReader = bankExcelReader;
    }

    public List<BankTransaction> readTransactions(
            InputStream input) throws IOException {

        return bankExcelReader.read(input);
    }

    public List<BankTransaction> extractWithdrawals(
            List<BankTransaction> transactions) {

        return transactions.stream()
                .filter(transaction ->
                        "출금".equals(transaction.direction()))
                .toList();
    }

    public List<BankTransaction> extractEventExpenses(
            List<BankTransaction> transactions) {

        return extractWithdrawals(transactions).stream()
                .filter(transaction ->
                        "행사 지출".equals(
                                transaction.transactionType()))
                .toList();
    }

    public List<BankTransaction> extractCarryoverWithdrawals(
            List<BankTransaction> transactions) {

        return extractWithdrawals(transactions).stream()
                .filter(transaction ->
                        "차년도 이월 출금".equals(
                                transaction.transactionType()))
                .toList();
    }

    public Map<Integer, WithdrawalSummary> summarizeWithdrawalsByYear(
            List<BankTransaction> transactions) {

        Map<Integer, WithdrawalSummary> summaries =
                new TreeMap<>();

        for (BankTransaction transaction
                : extractWithdrawals(transactions)) {

            int year = transaction.accountYear();

            WithdrawalSummary previous =
                    summaries.getOrDefault(
                            year,
                            new WithdrawalSummary(0, 0)
                    );

            summaries.put(
                    year,
                    new WithdrawalSummary(
                            Math.addExact(previous.count(), 1),
                            Math.addExact(
                                    previous.totalAmount(),
                                    transaction.amount())
                    )
            );
        }

        return summaries;
    }

    public record WithdrawalSummary(
            long count,
            long totalAmount
    ) {
    }
}
