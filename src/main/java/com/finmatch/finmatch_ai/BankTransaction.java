package com.finmatch.finmatch_ai;
//	은행 거래 한 건의 데이터를 담음

// 거래 고유번호, 거래 일시, 계좌 연도, 가상 계좌 식별번호, 거래처 또는 입금자
// 입금/출금 구분, 거래 금액, 거래 후 잔액, 거래 유형, 거래 메모
import java.time.LocalDateTime;

public record BankTransaction(
        String transactionId,
        LocalDateTime transactionDateTime,
        int accountYear,
        String accountId,
        String counterparty,
        String direction,
        long amount,
        long balanceAfter,
        String transactionType,
        String memo
) {
}
