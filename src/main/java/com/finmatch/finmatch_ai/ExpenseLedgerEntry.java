package com.finmatch.finmatch_ai;

//학생회 지출 장부의 한 행(지출 한 건)의 데이터를 담음

import java.time.LocalDateTime;

public record ExpenseLedgerEntry(
     String ledgerId,                // 명부 고유번호
     LocalDateTime expenseDateTime,  // 지출 날짜
     int accountYear,                // 계좌 연도
     String eventName,               // 행사명
     String expenseItem,             // 지출 항목
     String counterparty,            // 거래처
     long amount,                    // 기록된 금액
     String receiptNumber,           // 영수증 번호
     String memo                     // 지출 내용 및 비고
) {
}
