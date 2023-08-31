package ru.growapp.tftelegram.google.api;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Lists;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.growapp.tftelegram.model.TFCalendar;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class GoogleSheetsApiClient {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsApiClient.class);
    private static final String APPLICATION_NAME = "TERFIT";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE, SheetsScopes.DRIVE_FILE);
    private static final String SERVICE_CREDENTIALS_FILE_PATH = "/credentials-service.json";

    private static GoogleCredential getServiceCredentials() throws IOException {
        // Load client secrets.
        InputStream in = GoogleSheetsApiClient.class.getResourceAsStream(SERVICE_CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + SERVICE_CREDENTIALS_FILE_PATH);
        }

        return GoogleCredential.fromStream(in).createScoped(SCOPES);
    }


    public static ArrayList<TFCalendar> getCalendarData() throws IOException, GeneralSecurityException {
        ArrayList<TFCalendar> cals = Lists.newArrayList();

        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        final String spreadsheetId = "1WR-lxPNhLv4Jnl-3YRmcpCywiM9ds1didGMstPPLteE";
        Sheets service =
                new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getServiceCredentials())
                        .setApplicationName(APPLICATION_NAME)
                        .build();

        service.spreadsheets().get(spreadsheetId).setIncludeGridData(false).execute().getSheets().forEach(sheet -> {
            ValueRange response = null;
            try {
                response = service.spreadsheets().values()
                        .get(spreadsheetId, sheet.getProperties().getTitle()+"!A1:F")
                        .execute();

                List<List<Object>> values = response.getValues();
                if (values == null || values.isEmpty()) {
                    logger.warn("No data found.");
                } else {
                    for (List row : values) {
                        if (row.size() < 6) continue;

                        TFCalendar calendar = new TFCalendar();
                        calendar.setCalDate(row.get(0).toString());
                        calendar.setCalDay(row.get(1).toString());
                        calendar.setCalTime(row.get(2).toString());
                        calendar.setCalWorkout(row.get(3).toString());
                        calendar.setCalInstructor(row.get(4).toString());
                        calendar.setCalZone(row.get(5).toString());

                        cals.add(calendar);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to parse {} {}", sheet.getProperties().getTitle(), e);
            }
        });

        return cals;
    }
}
