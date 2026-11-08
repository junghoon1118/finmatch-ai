package com.finmatch.finmatch_ai;
// 학생회 장부 Excel의 연도별 시트를 읽어 장부 객체(ExpenseLedgerEntry)로 변환

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
public class LedgerExcelReader {

    // 읽을 시트 이름. TEST_ANSWER, GUIDE 시트는 여기에 없으므로 읽지 않는다.
    private static final List<String> YEAR_SHEETS =
            List.of("2022", "2023", "2024", "2025", "2026");

    // 첫 행(제목 행)에 있어야 하는 열 이름과 순서
    private static final List<String> HEADERS = List.of(
            "명부 고유번호",
            "지출 날짜",
            "계좌 연도",
            "행사명",
            "지출 항목",
            "거래처",
            "기록된 금액",
            "영수증 번호",
            "지출 내용 및 비고"
    );

    // 입력: 업로드된 Excel 파일의 데이터 흐름(InputStream)
    // 반환: 장부 한 건씩 담긴 목록
    public List<ExpenseLedgerEntry> read(InputStream input)
            throws IOException {

        Objects.requireNonNull(input, "입력 스트림이 필요합니다.");

        List<ExpenseLedgerEntry> entries = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(input)) {

            // 2022 ~ 2026 시트를 하나씩 처리
            for (String sheetName : YEAR_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);

                if (sheet == null) {
                    throw error(sheetName, 0, 0, "필수 연도 시트가 없습니다.");
                }

                validateHeader(sheet);

                // 0번 행은 제목이므로 1번 행부터 마지막 행까지 반복
                for (int rowIndex = 1;
                        rowIndex <= sheet.getLastRowNum();
                        rowIndex++) {

                    Row row = sheet.getRow(rowIndex);

                    // 완전히 빈 행은 건너뛴다
                    if (isCompletelyBlank(row)) {
                        continue;
                    }

                    // 한 행을 장부 객체 하나로 만들어 목록에 추가한다.
                    // 중복처럼 보이는 행도 지우지 않고 모두 담는다.
                    entries.add(readEntry(sheetName, row));
                }
            }
        }

        return List.copyOf(entries);
    }

    // 첫 행의 열 이름과 순서가 예상과 같은지 검사
    private void validateHeader(Sheet sheet) {
        Row header = sheet.getRow(0);

        for (int column = 0; column < HEADERS.size(); column++) {
            Cell cell = header == null ? null : header.getCell(column);

            if (cell == null
                    || cell.getCellType() != CellType.STRING
                    || !HEADERS.get(column).equals(cell.getStringCellValue())) {

                throw error(sheet.getSheetName(), 0, column,
                        "헤더 이름 또는 순서가 잘못되었습니다. 예상 헤더: "
                                + HEADERS.get(column));
            }
        }
    }

    // 한 행을 읽어 ExpenseLedgerEntry 하나로 변환
    private ExpenseLedgerEntry readEntry(String sheetName, Row row) {

        int rowIndex = row.getRowNum();

        String ledgerId = readText(sheetName, row, 0, true);
        LocalDateTime expenseDateTime = readDateTime(sheetName, row, 1);

        long yearValue = readInteger(sheetName, row, 2);
        if (yearValue != Integer.parseInt(sheetName)) {
            throw error(sheetName, rowIndex, 2,
                    "계좌 연도가 시트 연도와 다릅니다.");
        }

        String eventName = readText(sheetName, row, 3, true);
        String expenseItem = readText(sheetName, row, 4, true);
        String counterparty = readText(sheetName, row, 5, true);

        long amount = readInteger(sheetName, row, 6);
        if (amount <= 0) {
            throw error(sheetName, rowIndex, 6,
                    "기록된 금액은 0보다 큰 정수여야 합니다.");
        }

        String receiptNumber = readText(sheetName, row, 7, true);

        // 비고만 비어 있어도 된다 (required = false)
        String memo = readText(sheetName, row, 8, false);

        return new ExpenseLedgerEntry(
                ledgerId, expenseDateTime, (int) yearValue,
                eventName, expenseItem, counterparty,
                amount, receiptNumber, memo);
    }

    // 문자열 셀 읽기. required가 true인데 비어 있으면 오류
    private String readText(String sheetName, Row row,
            int column, boolean required) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            if (required) {
                throw error(sheetName, row.getRowNum(), column,
                        "필수값이 비어 있습니다.");
            }
            return "";
        }

        if (cell.getCellType() != CellType.STRING) {
            throw error(sheetName, row.getRowNum(), column,
                    "문자열 셀이어야 합니다.");
        }

        return cell.getStringCellValue();
    }

    // 정수 셀 읽기. 소수가 있으면 버리지 않고 오류로 처리
    private long readInteger(String sheetName, Row row, int column) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            throw error(sheetName, row.getRowNum(), column,
                    "필수 숫자가 비어 있습니다.");
        }

        if (cell.getCellType() != CellType.NUMERIC
                || DateUtil.isCellDateFormatted(cell)) {
            throw error(sheetName, row.getRowNum(), column,
                    "날짜나 문자열이 아닌 숫자 셀이어야 합니다.");
        }

        double value = cell.getNumericCellValue();

        try {
            return BigDecimal.valueOf(value).longValueExact();
        } catch (ArithmeticException e) {
            throw error(sheetName, row.getRowNum(), column,
                    "정수여야 합니다. 입력값: " + value);
        }
    }

    // 날짜 셀 읽기. 시간까지 함께 읽는다
    private LocalDateTime readDateTime(String sheetName, Row row, int column) {

        Cell cell = row.getCell(column);

        if (isBlank(cell)) {
            throw error(sheetName, row.getRowNum(), column,
                    "지출 날짜가 비어 있습니다.");
        }

        if (cell.getCellType() != CellType.NUMERIC
                || !DateUtil.isCellDateFormatted(cell)
                || !DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            throw error(sheetName, row.getRowNum(), column,
                    "Excel 날짜 셀이어야 합니다.");
        }

        return cell.getLocalDateTimeCellValue();
    }

    // 행의 모든 칸이 비어 있는지 검사
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

    // 칸이 비어 있는지 검사 (null, 빈 칸, 공백만 있는 문자열)
    private boolean isBlank(Cell cell) {
        return cell == null
                || cell.getCellType() == CellType.BLANK
                || (cell.getCellType() == CellType.STRING
                    && cell.getStringCellValue().isBlank());
    }

    // 오류 메시지에 시트명, 행 번호, 열 이름을 넣어 만든다
    private IllegalArgumentException error(String sheetName,
            int rowIndex, int column, String message) {

        String columnName = column < HEADERS.size()
                ? HEADERS.get(column) : "추가 열";

        return new IllegalArgumentException(
                "시트 [" + sheetName + "], 행 " + (rowIndex + 1)
                        + ", 열 [" + columnName + "]: " + message);
    }
}