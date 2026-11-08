package com.finmatch.finmatch_ai;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LedgerExcelReadExample {
    public static void main(String[] args) throws Exception {
        Path file = Path.of("data", "student_council_ledger.xlsx");
        System.out.println("찾는 파일 위치: " + file.toAbsolutePath());
        System.out.println("파일 존재 여부: " + Files.exists(file));

        LedgerExcelReader reader = new LedgerExcelReader();
        try (InputStream input = Files.newInputStream(file)) {
            List<ExpenseLedgerEntry> entries = reader.read(input);
            System.out.println("전체 장부: " + entries.size() + "건");

            Map<Integer, Integer> byYear = new TreeMap<>();
            for (ExpenseLedgerEntry e : entries) {
                byYear.merge(e.accountYear(), 1, Integer::sum);
            }
            byYear.forEach((year, count) ->
                    System.out.println(year + "년: " + count + "건"));

            System.out.println("첫 번째 장부: " + entries.get(0));
        }
    }
}