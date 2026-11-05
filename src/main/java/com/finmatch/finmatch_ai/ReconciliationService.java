package com.finmatch.finmatch_ai;
// 은행 행사 지출 목록과 학생회 장부 목록을 비교하는 클래스

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiPredicate;

import org.springframework.stereotype.Service;

import com.finmatch.finmatch_ai.ReconciliationResult.Status;

@Service
public class ReconciliationService {

    // 날짜가 달라도 "같은 거래일 수 있다"고 볼 최대 날짜 차이 (일). 기준을 바꾸려면 이 숫자를 고친다.
    private static final long MAX_DATE_GAP_DAYS = 3;

    // 입력: 은행의 "행사 지출" 출금 목록, 학생회 장부 목록
    // 반환: 비교 결과 목록 (은행 거래마다 1건 + 장부 중복/장부만 있는 건)
    public List<ReconciliationResult> reconcile(
            List<BankTransaction> bankExpenses,
            List<ExpenseLedgerEntry> ledgerEntries) {

        // 아직 짝을 못 찾은 은행 거래와 장부. 짝을 찾으면 여기서 지운다.
        List<BankTransaction> unmatchedBank = new ArrayList<>(bankExpenses);
        List<ExpenseLedgerEntry> unusedLedger = new ArrayList<>(ledgerEntries);
        List<ReconciliationResult> results = new ArrayList<>();

        // 엄격한 기준부터 느슨한 기준 순서로 4번 짝을 찾는다.
        // 한 번에 하나씩만 짝지으므로 장부 한 건이 은행 두 건에 쓰이지 않는다.

        // 1순위: 연도, 날짜, 금액, 거래처가 모두 같다 -> 일치
        pair(unmatchedBank, unusedLedger, results, Status.MATCHED,
                "날짜, 금액, 거래처가 모두 같습니다.",
                (b, l) -> sameYear(b, l) && sameDate(b, l) && sameAmount(b, l)
                        && b.counterparty().equals(l.counterparty()));

        // 2순위: 날짜, 금액은 같고 거래처 이름만 조금 다르다
        pair(unmatchedBank, unusedLedger, results, Status.VENDOR_NAME_DIFFERENCE,
                "날짜와 금액은 같지만 거래처 표기가 다릅니다.",
                (b, l) -> sameYear(b, l) && sameDate(b, l) && sameAmount(b, l)
                        && similarVendor(b.counterparty(), l.counterparty()));

        // 3순위: 금액, 거래처는 같고 날짜가 며칠 차이 난다
        pair(unmatchedBank, unusedLedger, results, Status.DATE_MISMATCH,
                "금액과 거래처는 같지만 날짜가 다릅니다.",
                (b, l) -> sameYear(b, l) && sameAmount(b, l) && !sameDate(b, l)
                        && dateGapDays(b, l) <= MAX_DATE_GAP_DAYS
                        && similarVendor(b.counterparty(), l.counterparty()));

        // 4순위: 날짜, 거래처는 같고 금액이 다르다
        pair(unmatchedBank, unusedLedger, results, Status.AMOUNT_MISMATCH,
                "날짜와 거래처는 같지만 금액이 다릅니다.",
                (b, l) -> sameYear(b, l) && sameDate(b, l) && !sameAmount(b, l)
                        && similarVendor(b.counterparty(), l.counterparty()));

        // 끝까지 짝이 없는 은행 거래 -> 장부 누락 의심
        for (BankTransaction bank : unmatchedBank) {
            results.add(new ReconciliationResult(Status.MISSING_IN_LEDGER, bank,
                    List.of(), "은행 출금에 대응하는 장부를 찾지 못했습니다."));
        }

        // 남은 장부: 이미 짝지어진 장부와 영수증 번호가 같으면 중복, 아니면 은행에 없는 장부
        for (ExpenseLedgerEntry extra : unusedLedger) {
            results.add(classifyLeftover(extra, results));
        }

        results.sort(Comparator.comparing(ReconciliationService::sortKey));
        return List.copyOf(results);
    }

    // 짝 찾기 한 번: 아직 짝이 없는 은행 거래마다 rule을 만족하는 첫 장부를 찾아 짝짓는다.
    private void pair(List<BankTransaction> unmatchedBank,
            List<ExpenseLedgerEntry> unusedLedger,
            List<ReconciliationResult> results,
            Status status,
            String reason,
            BiPredicate<BankTransaction, ExpenseLedgerEntry> rule) {

        Iterator<BankTransaction> bankIterator = unmatchedBank.iterator();

        while (bankIterator.hasNext()) {
            BankTransaction bank = bankIterator.next();

            for (ExpenseLedgerEntry ledger : unusedLedger) {
                if (rule.test(bank, ledger)) {
                    results.add(new ReconciliationResult(
                            status, bank, List.of(ledger), reason));
                    bankIterator.remove();      // 이 은행 거래는 짝을 찾았으니 제외
                    unusedLedger.remove(ledger); // 이 장부도 다시 쓰지 않는다
                    break;
                }
            }
        }
    }

    // 짝이 없는 장부 한 건이 중복인지, 은행에 없는 장부인지 판단
    private ReconciliationResult classifyLeftover(
            ExpenseLedgerEntry extra, List<ReconciliationResult> results) {

        for (ReconciliationResult result : results) {
            if (result.ledgerEntries().isEmpty()) {
                continue;
            }
            ExpenseLedgerEntry original = result.ledgerEntries().get(0);

            if (original.receiptNumber().equals(extra.receiptNumber())) {
                return new ReconciliationResult(Status.DUPLICATE_LEDGER,
                        result.bankTransaction(), List.of(original, extra),
                        "같은 영수증 번호의 장부가 두 건 이상 있습니다.");
            }
        }

        return new ReconciliationResult(Status.LEDGER_ONLY, null,
                List.of(extra), "대응하는 은행 출금이 없는 장부입니다.");
    }

    private boolean sameYear(BankTransaction b, ExpenseLedgerEntry l) {
        return b.accountYear() == l.accountYear();
    }

    // 시각은 무시하고 날짜(연-월-일)만 비교
    private boolean sameDate(BankTransaction b, ExpenseLedgerEntry l) {
        return b.transactionDateTime().toLocalDate()
                .equals(l.expenseDateTime().toLocalDate());
    }

    private boolean sameAmount(BankTransaction b, ExpenseLedgerEntry l) {
        return b.amount() == l.amount();
    }

    private long dateGapDays(BankTransaction b, ExpenseLedgerEntry l) {
        return Math.abs(ChronoUnit.DAYS.between(
                b.transactionDateTime().toLocalDate(),
                l.expenseDateTime().toLocalDate()));
    }

    // 거래처 이름이 "같은 곳으로 보이는지" 판단: 띄어쓰기와 '가상' 접두어를 뺀 뒤 같거나 포함 관계
    private boolean similarVendor(String a, String b) {
        String x = normalizeVendor(a);
        String y = normalizeVendor(b);

        if (x.equals(y)) {
            return true;
        }
        return x.length() >= 2 && y.length() >= 2
                && (x.contains(y) || y.contains(x));
    }

    private String normalizeVendor(String name) {
        String normalized = name.replace(" ", "");
        if (normalized.startsWith("가상")) {
            normalized = normalized.substring(2);
        }
        return normalized;
    }

    // 결과를 날짜순으로 보여 주기 위한 정렬 기준
    private static java.time.LocalDateTime sortKey(ReconciliationResult result) {
        if (result.bankTransaction() != null) {
            return result.bankTransaction().transactionDateTime();
        }
        return result.ledgerEntries().get(0).expenseDateTime();
    }
}
