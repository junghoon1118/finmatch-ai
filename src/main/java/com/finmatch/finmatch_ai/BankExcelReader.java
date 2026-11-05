package com.finmatch.finmatch_ai;
// 	은행 엑셀의 연도별 시트를 읽어 거래 객체로 변환

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class BankExcelReader {

    private static final List<String> YEAR_SHEETS =
            List.of("2022", "2023", "2024", "2025", "2026");

    private static final List<String> HEADERS = List.of(
            "거래 고유번호",
            "거래 일시",
            "계좌 연도",
            "가상 계좌 식별번호",
            "거래처 또는 입금자",
            "입금/출금 구분",
            "거래 금액",
            "거래 후 잔액",
            "거래 유형",
            "거래 메모"
    );

    public List<BankTransaction> read(InputStream input)
            throws IOException {

        Objects.requireNonNull(input, "입력 스트림이 필요합니다.");

        List<BankTransaction> transactions = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(input)) {

            for (String sheetName : YEAR_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);

                if (sheet == null) {
                    throw error(
                            sheetName, 0, 0,
                            "필수 연도 시트가 없습니다."
                    );
                }

                validateHeader(sheet);

                for (int rowIndex = 1;
                        rowIndex <= sheet.getLastRowNum();
                        rowIndex++) {

                    Row row = sheet.getRow(rowIndex);

                    if (isCompletelyBlank(row)) {
                        continue;
                    }

                    transactions.add(
                            readTransaction(sheetName, row)
                    );
                }
            }
        }

        return List.copyOf(transactions);
    }

    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);

        for (int column = 0; column < HEADERS.size(); column++) {
            Cell cell = header == null
                    ? null
                    : header.getCell(column);

            if (cell == null
                    || cell.getCellType() != CellType.STRING
                    || !HEADERS.get(column)
                            .equals(cell.getStringCellValue())) {

                throw error(
                        sheet.getSheetName(), 0, column,
                        "헤더 이름 또는 순서가 잘못되었습니다. "
                                + "예상 헤더: " + HEADERS.get(column)
                );
            }
        }

        // 정의된 10개 열 뒤에 별도 데이터 열이 있는지 확인합니다.
        for (int column = HEADERS.size();
                column < header.getLastCellNum();
                column++) {

            if (!isBlank(header.getCell(column))) {
                throw error(
                        sheet.getSheetName(), 0, column,
                        "예상하지 않은 추가 헤더입니다."
                );
            }
        }
    }

    private BankTransaction readTransaction(
            String sheetName, Row row) {

        int rowIndex = row.getRowNum();

        String transactionId =
                readText(sheetName, row, 0, true);

        LocalDateTime transactionDateTime =
                readDateTime(sheetName, row, 1);

        long yearValue = readInteger(sheetName, row, 2);

        if (yearValue != Integer.parseInt(sheetName)) {
            throw error(
                    sheetName, rowIndex, 2,
                    "계좌 연도가 시트 연도와 다릅니다."
            );
        }

        String accountId =
                readText(sheetName, row, 3, true);

        String counterparty =
                readText(sheetName, row, 4, true);

        String direction =
                readText(sheetName, row, 5, true);

        if (!direction.equals("입금")
                && !direction.equals("출금")) {
            throw error(
                    sheetName, rowIndex, 5,
                    "'입금' 또는 '출금'이어야 합니다."
            );
        }

        long amount = readInteger(sheetName, row, 6);

        if (amount <= 0) {
            throw error(
                    sheetName, rowIndex, 6,
                    "거래 금액은 0보다 큰 정수여야 합니다."
            );
        }

        long balanceAfter =
                readInteger(sheetName, row, 7);

        String transactionType =
                readText(sheetName, row, 8, true);

        // 메모만 빈 값을 허용합니다.
        String memo =
                readText(sheetName, row, 9, false);

        return new BankTransaction(
                transactionId,
                transactionDateTime,
                (int) yearValue,
                accountId,
                counterparty,
                direction,
                amount,
                balanceAfter,
                transactionType,
                memo
        );
    }

    private String readText(
            String sheetName,
            Row row,
            int column,
            boolean required) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            if (required) {
                throw error(
                        sheetName, row.getRowNum(), column,
                        "필수값이 비어 있습니다."
                );
            }

            return "";
        }

        if (cell.getCellType() != CellType.STRING) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "문자열 셀이어야 합니다."
            );
        }

        // 원본 문자열을 그대로 반환합니다.
        return cell.getStringCellValue();
    }

    private long readInteger(
            String sheetName, Row row, int column) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "필수 숫자가 비어 있습니다."
            );
        }

        if (cell.getCellType() != CellType.NUMERIC
                || DateUtil.isCellDateFormatted(cell)) {

            throw error(
                    sheetName, row.getRowNum(), column,
                    "날짜나 문자열이 아닌 숫자 셀이어야 합니다."
            );
        }

        double value = cell.getNumericCellValue();

        if (!Double.isFinite(value)) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "유효한 숫자가 아닙니다."
            );
        }

        try {
            // 소수나 long 범위 밖의 값은 예외가 발생합니다.
            // (long) 형변환으로 소수를 버리지 않습니다.
            return BigDecimal.valueOf(value).longValueExact();

        } catch (ArithmeticException e) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "long 범위의 정수여야 합니다. 입력값: " + value
            );
        }
    }

    private LocalDateTime readDateTime(
            String sheetName, Row row, int column) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "거래 일시가 비어 있습니다."
            );
        }

        if (cell.getCellType() != CellType.NUMERIC
                || !DateUtil.isCellDateFormatted(cell)
                || !DateUtil.isValidExcelDate(
                        cell.getNumericCellValue())) {

            throw error(
                    sheetName, row.getRowNum(), column,
                    "Excel 날짜 셀이어야 합니다."
            );
        }

        try {
            LocalDateTime value =
                    cell.getLocalDateTimeCellValue();

            if (value == null) {
                throw error(
                        sheetName, row.getRowNum(), column,
                        "거래 일시를 읽을 수 없습니다."
                );
            }

            return value;

        } catch (java.time.DateTimeException e) {
            throw error(
                    sheetName, row.getRowNum(), column,
                    "유효하지 않은 거래 일시입니다."
            );
        }
    }

    private boolean isCompletelyBlank(Row row) {
        if (row == null) {
            return true;
        }

        for (Cell cell : row) {
            if (!isBlank(cell)) {
                return false;
            }
        }

        return true;
    }

    private boolean isBlank(Cell cell) {
        return cell == null
                || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().isBlank());
    }

    private IllegalArgumentException error(
            String sheetName,
            int rowIndex,
            int column,
            String message) {

        String columnName = column < HEADERS.size()
                ? HEADERS.get(column)
                : "추가 열";

        return new IllegalArgumentException(
                "시트 [" + sheetName + "]"
                        + ", 행 " + (rowIndex + 1)
                        + ", 열 [" + columnName + "]"
                        + ": " + message
        );
    }
}
