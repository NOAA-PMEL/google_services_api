import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
//import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.util.Collections;
import java.util.List;

public class SheetsQuickstart {
    private static final String APPLICATION_NAME = "Google Sheets API Java Quickstart";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String DRIVE_TOKENS_DIRECTORY_PATH = "drive_tokens";
    private static final String SHEETS_TOKENS_DIRECTORY_PATH = "sheets_tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SHEETS_SCOPES =
            Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final List<String> DRIVE_SCOPES =
            Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT,
                                             String tokensDir,
                                             List<String> scopes)
            throws IOException {
        // Load client secrets.
        InputStream in = SheetsQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(tokensDir)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private static File getFileLike(Drive service, String name_match) throws IOException {
        FileList result = service.files().list()
                .setCorpora("user")
                .setQ("name contains '" + name_match + "'")
//                         //   " and modifiedTime > '2022-11-26'") // 'OHCA'")
                .setPageSize(20)
                .setOrderBy("name")
                .setFields("files(id, name, kind, mimeType, owners, parents)")
//                .setPageToken(pageToken)
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            System.out.println("No files found.");
            return null;
        } else {
            if (files.size() > 1) {
                System.out.println("WARNING: MORE THAN 1 FILE FOUND FOR NAME MATCH: " + name_match);
            }
            return files.get(0);
        }
    }

            /**
             * Prints the names and majors of students in a sample spreadsheet:
             * https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit
             */
    public static void main(String... args) throws IOException, GeneralSecurityException {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
//        final String opsFloatsSreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms";
        final String copyFloatsSreadsheetId = "15lQRVE30YiJBZCFDxXMaqlC5cuYdQNKgYt4VP00NS0E";
        final String coreMetadataSpreadsheetId = "1WkKCtoTwjpGhH_Dl8BD_DwXgzyQR16Bq";
        String spreadsheetId = copyFloatsSreadsheetId;
//        final String range = "Class Data!A2:E";
        final String range = "Data";
        Drive driveService =
                new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                                    getCredentials(HTTP_TRANSPORT,
                                                   DRIVE_TOKENS_DIRECTORY_PATH,
                                                   DRIVE_SCOPES))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
        File sheetFile = getFileLike(driveService, "dev test prototype"); // "Tomcat Ports"); // "MRV_Core_IMEIs"); // "Copy of MRV_CORE");
        if ( sheetFile == null ) {
            System.err.println("No drive file found!");
            System.exit(-1);
        }
        spreadsheetId = sheetFile.getId();
        Sheets sheetsService =
                new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                                    getCredentials(HTTP_TRANSPORT,
                                                   SHEETS_TOKENS_DIRECTORY_PATH,
                                                   SHEETS_SCOPES))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
        Spreadsheet ssheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        System.out.println("ssheet:" + ssheet.toPrettyString());
        List<Sheet> sheets = ssheet.getSheets();
        for (Sheet sheet : sheets ) {
            System.out.println("sheet: " + sheet.toPrettyString());
        }
        Sheet sheet = sheets.get(0);
        Sheets.Spreadsheets ssheets = sheetsService.spreadsheets();
//        List<List<Object>> values = Collections.singletonList(
//                                        Collections.singletonList(
//                                           new Float("42.42")
//                                        ));
//        ValueRange update = new ValueRange().setValues(values);
//        UpdateValuesResponse response = ssheets.values().update(spreadsheetId, "H3",update)
//                .setValueInputOption("USER_ENTERED")
//                .execute();
//        System.out.println("update response: " + response);

//        ssheets.values().update(spreadsheetId, "Sheet1", ValueRange
        ValueRange response = ssheets.values()
                .get(spreadsheetId, "Sheet1")
                .execute();
        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            System.out.println("No data found.");
        } else {
            for (List row : values) {
                // Print columns A and E, which correspond to indices 0 and 4.
                System.out.printf("%s, %s\n", row.get(1), row.get(columnFor('D')));
            }
        }
    }
    static int columnFor(char c) {
        char col = String.valueOf(c).toUpperCase().charAt(0);
        return (int)c - (int)'A';
    }

}