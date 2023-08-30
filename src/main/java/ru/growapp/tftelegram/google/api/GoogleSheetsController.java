package ru.growapp.tftelegram.google.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.growapp.tftelegram.model.TFCalendar;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleSheetsController {
    Logger logger = LoggerFactory.getLogger(GoogleSheetsController.class);

    public GoogleSheetsController() {
    }

    public List<TFCalendar> getTodaySpreadsheetData() {
        LocalDateTime now = LocalDateTime.now();
        try {
            List<TFCalendar> calendarData = GoogleSheetsApiClient.getCalendarData().stream().filter(c -> {
                try {
                    LocalDateTime calDateTime = LocalDateTime.parse(String.format("%s %s", c.getCalDate(), c.getCalTime().length() < 5 ? "0" + c.getCalTime() : c.getCalTime()), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                    return now.isBefore(calDateTime) && now.toLocalDate().atStartOfDay().equals(calDateTime.toLocalDate().atStartOfDay());
                } catch (Exception e) {
//                    logger.error("Failed to parse date", e);
                    // skip
                }
                return false;
            }).collect(Collectors.toList());
            return calendarData;
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error retrieving spreadsheet data", e);
        }

        return new ArrayList<>();
    }

    public List<TFCalendar> getTomorrowSpreadsheetData() {
        LocalDate now = LocalDate.now().plusDays(1);
        try {
            List<TFCalendar> calendarData = GoogleSheetsApiClient.getCalendarData().stream().filter(c -> {
                try {
                    LocalDate calDateTime = LocalDate.parse(c.getCalDate(), DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                    return now.atStartOfDay().equals(calDateTime.atStartOfDay());
                } catch (Exception e) {
//                    logger.error("Failed to parse date", e);
                    // skip
                }
                return false;
            }).collect(Collectors.toList());
            return calendarData;
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error retrieving spreadsheet data", e);
        }

        return new ArrayList<>();
    }

    public List<TFCalendar> get7daysSpreadsheetData() {
        LocalDateTime now = LocalDateTime.now();
        try {
            List<TFCalendar> calendarData = GoogleSheetsApiClient.getCalendarData().stream().filter(c -> {
                try {
                    LocalDateTime calDateTime = LocalDateTime.parse(String.format("%s %s", c.getCalDate(), c.getCalTime().length() < 5 ? "0" + c.getCalTime() : c.getCalTime()), DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                    return calDateTime.isAfter(now) && calDateTime.isBefore(now.plusDays(8).toLocalDate().atStartOfDay());
                } catch (Exception e) {
//                    logger.error("Failed to parse date", e);
                    // skip
                }
                return false;
            }).collect(Collectors.toList());
            return calendarData;
        } catch (IOException | GeneralSecurityException e) {
            logger.error("Error retrieving spreadsheet data", e);
        }
        return new ArrayList<>();
    }
}
