package com.finmatch.finmatch_ai;
// 은행 거래와 학생회 장부를 비교한 결과 한 건

import java.util.List;

public record ReconciliationResult(
        Status status,                          // 판정 결과
        BankTransaction bankTransaction,        // 대응하는 은행 거래 (장부에만 있으면 null)
        List<ExpenseLedgerEntry> ledgerEntries, // 대응하는 장부 (누락이면 빈 목록)
        String reason                           // 그렇게 판단한 이유
) {

    // 판정 종류. label은 화면이나 보고서에 보여 줄 한글 이름
    public enum Status {
        MATCHED("일치"),
        VENDOR_NAME_DIFFERENCE("거래처 표기 차이(동일 가능성)"),
        DATE_MISMATCH("날짜 불일치 의심"),
        AMOUNT_MISMATCH("금액 불일치 의심"),
        MISSING_IN_LEDGER("장부 누락 의심"),
        DUPLICATE_LEDGER("중복 장부 기록 의심"),
        LEDGER_ONLY("은행 거래 없는 장부");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
