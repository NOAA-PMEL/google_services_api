// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.


package gov.noaa.pmel.sdig;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.DriveList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import gov.noaa.pmel.tws.util.StringUtils;
import gov.noaa.pmel.tws.util.cli.CLClient;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

/** This application provides upload to, download from, and listing of
 * the user's Google Drive folders.
 *
 * To use the application, authentication tokens need to be created.
 * To create the tokens, the application must be run interactively.
 * If the tokens are not present or are invalid, the application will launch
 * the default browser with a Google SSO prompt.
 * If the user is not logged into their Google account in the default browser,
 * then the user must either log in separately, or paste the URL provided in
 * the console into a browser window in the browser in which the user is logged in.
 *
 * The "Access blocked: cnsd-dev-mail-test can only be used within its organization"
 * error message on the sign-in page indicates that the user is not logged into their
 * Google account in the browser being used.
 *
 * Once the tokens are created, the application can be run in a non-interactive,
 * scripted mode.
*/
public class DriveMaster extends CLClient {

    private enum EXISTING_OPTIONS {
        PRESERVE,
        REPLACE,
        KEEP
    }

    private static final String GDRIVE_TYPE_INDICATOR = "application/vnd.google-apps.";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String SHEETS_MIME_TYPE = "application/vnd.google-apps.sheets";
    private static final String SPREADSHEET_MIME_TYPE = "application/vnd.google-apps.spreadsheet";
    private static final String DOCUMENTS_MIME_TYPE = "application/vnd.google-apps.documents";
    private  static final String SHORTCUT_MIME_TYPE = "application/vnd.google-apps.shortcut";
    private static final String FOLDER_CONTENTS_REGEX = ".*/(\\*)?$";
    private static final String SHARED_INDICATOR = "shared:";

    private enum GOOGLE_DRIVE_TYPE {
        SHEET("sheets", SHEETS_MIME_TYPE),
        DOC("document", DOCUMENTS_MIME_TYPE);
        final String displayName;
        final String mimeType;
        GOOGLE_DRIVE_TYPE(String name, String type) {
            displayName = name;
            mimeType = type;
        }
        GOOGLE_DRIVE_TYPE forType(String mimeType) {
            switch (mimeType) {
                case SHEETS_MIME_TYPE:
                    return GOOGLE_DRIVE_TYPE.SHEET;
                case DOCUMENTS_MIME_TYPE:
                    return GOOGLE_DRIVE_TYPE.DOC;
                default:
                    return null;
            }
        }
    }

    private static final String SLASH = java.io.File.separator;
    private static final char cSLASH = java.io.File.separatorChar;

    static CMD cmd = null;
    static String fromFilePath = null;
    static String toFilePath = null;
    static String root_drive = "root";
    static boolean recurse = true;  //  currently doesn't change
    static boolean doit = true;
    static boolean skipRoot = false;
    static boolean doAuth = false;
    static boolean verbose = false;
    static boolean quiet = false;
    static boolean debug = false;
    // DEFAULT: adds another instance if target file(name) exists
    static boolean preserve = false;
    // overwrites target file instance if exists
    static boolean replace = false;
    // does not transfer file if target exists
    static boolean keep_original = false;
    static boolean YES = false;
    static boolean IGNORE_MIME = false;

    static String[] helps = new String[] { "-?", "-h", "--help", "help" };
    static final List<String> helpArgs = Arrays.asList(helps);
    static final List<String> authArgs = Arrays.asList("auth", "--auth");
    static final List<String> commands = Arrays.asList("up", "put", "down", "get", "list", "auth", "--auth");

    /**
     * keep track of created folders, so they can be deleted after dry-run.
     * Unless I think of a better way...
     */
    static List<File> createdFolders = new ArrayList<File>();

    // Get the location of the class, which can give you the location of the jar.
    private static String byGetProtectionDomain(Class clazz) {
        try {
            URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
            return Paths.get(url.toURI()).toString();
        } catch (URISyntaxException uxe) {
            System.err.println("WARN: Unable to find code source for token dir.");
            return null;
        }
    }

//
//    private CLOption optFromDir = CLOption.builder()
//                                    .flag("-f")
//                                    .longFlag("--fromdir")
//                                    .name("from_dir")
//                                    .description("The directory (local or remote) from which to transfer files.")
//                                    .build();
//    private CLOption optToDir = CLOption.builder()
//                                    .flag("-t")
//                                    .longFlag("--todir")
//                                    .name("to_dir")
//                                    .description("The directory (local or remote) into which to transfer files.")
//                                    .build();
//    private CLOption optOwner = CLOption.builder()
//                                    .flag("-o")
//                                    .longFlag("--owner")
//                                    .name("owner")
//                                    .description("The owner of the GDrive files.")
//                                    .build();
//    private CLOption optFileNameMatch = CLOption.builder()
//                                    .flag("-n")
//                                    .longFlag("--matches")
//                                    .name("name_match")
//                                    .description("A regular expression (???!) for filename matching for file selection.")
//                                    .build();
//    private CLCommand cmdUpload = CLCommand.builder()
//            .name("upload")
//            .command("up")
//            .description("Upload local files to GDrive.")
//            .option(optFromDir.toBuilder()
//                    .description("The local directory from which to upload files.")
//                    .build())
//            .option(optToDir.toBuilder()
//                    .description("The GDrive folder into which to upload files.")
//                    .build())
//            .option(optFileNameMatch)
//            .build();
//    private CLCommand cmdDownload = CLCommand.builder()
//            .name("download")
//            .command("down")
//            .description("Download GDrive files to local directory.")
//            .option(optFromDir.toBuilder()
//                    .description("The GDrive folder from which to download files.")
//                    .build())
//            .option(optToDir.toBuilder()
//                    .description("The local directory into which to download files.")
//                    .build())
//            .option(optFileNameMatch)
//            .option(optOwner)
//            .build();

    private static enum CMD {
        UP("up","local","drive"),
        DOWN("down","drive","local"),
        LIST("list", "drive", "local"),
        AUTH("auth", "na", "na");

        private String _movement;
        private String _from;
        private String _to;
        CMD(String movement, String fromLoc, String toLoc) {
            _movement = " " + movement + " to ";
            _from = fromLoc;
            _to = toLoc;
        }
        static CMD forArg(String arg) {
            if ( arg.startsWith("--")) arg = arg.substring(2);
            String arglc = arg.toLowerCase();
            CMD cmd = null;
            switch(arglc) {
                case "up":
                case "put":
                    cmd = UP;
                    break;
                case "down":
                case "get":
                    cmd = DOWN;
                    break;
                case "list":
                    cmd = LIST;
                    break;
                case "auth":
                    cmd = AUTH;
                    break;
                default:
                    if ( arglc.startsWith("down")) cmd = DOWN;
                    else if ( arglc.startsWith("up")) cmd = UP;
                    else if (arglc.startsWith("list"))  cmd = LIST;
                    else if (arglc.startsWith("auth"))  cmd = AUTH;
            }
            return cmd;
        }
        String from() { return " " + _from + " file "; }
        String to() { return " " + _to + " file "; }
        String movement() { return _movement; }
    }

    /** Application name. */
    private static final String APPLICATION_NAME = "Google Drive File Transfer Client";
    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
//    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /** Directory to store authorization tokens for this application. */
    private static final String TOKENS_DIRECTORY_PATH = "drive_tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
//    private static final String CREDENTIALS_FILE_PATH = "/client_secret_948816630488-n9ss7qdprtctu3k0v9is8u33m6c34olc.apps.googleusercontent.com.json";

    private static java.io.File getTokenPath() {
        String userHome = System.getProperty("user.home");
        String jarPath = byGetProtectionDomain(DriveMaster.class);
        debug("jarPath:"+jarPath);
        java.io.File tokenDir = new java.io.File(TOKENS_DIRECTORY_PATH);
        if ( tokenDir.exists() && tokenDir.isDirectory()) {
            if ( debug ) System.out.println("Found token dir: " + tokenDir.getAbsolutePath());
            return tokenDir;
        }
        if ( ! StringUtils.emptyOrNull(jarPath)) {
            String codePath = jarPath.startsWith("file:") ? jarPath.substring(5) : jarPath;
            if ( codePath.endsWith(".jar")) {
                codePath = codePath.substring(0, codePath.lastIndexOf(cSLASH));
            }
            debug("token codePath:"+codePath);
            codePath += SLASH+TOKENS_DIRECTORY_PATH;
            tokenDir = new java.io.File(codePath);
            if ( tokenDir.exists() && tokenDir.isDirectory()) {
                if ( debug ) System.out.println("Found token dir: " + tokenDir.getAbsolutePath());
                return tokenDir;
            }
        }
        if ( !StringUtils.emptyOrNull(userHome)) {
            tokenDir = new java.io.File(userHome+SLASH+TOKENS_DIRECTORY_PATH);
            if ( tokenDir.exists() && tokenDir.isDirectory()) {
                if ( debug ) System.out.println("Found token dir: " + tokenDir.getAbsolutePath());
                return tokenDir;
            }
        }
        if ( tokenDir != null && !(tokenDir.exists() && tokenDir.isDirectory() && tokenDir.canWrite())) {
            tokenDir = new java.io.File(TOKENS_DIRECTORY_PATH);
            System.err.println("###");
            System.err.println("### WARN: Token dir not found. Creating token dir in working directory: " +
                                tokenDir.getAbsolutePath());
            System.err.println("###");
            if ( ! tokenDir.mkdirs() ) {
                throw new IllegalStateException("Unable to create token dir: " + tokenDir.getAbsolutePath());
            }
            return tokenDir;
        }
        throw new IllegalStateException("Unable to find or create token dir "
                                        + TOKENS_DIRECTORY_PATH
                                        + " in current working dir, jar file location, or user home.");
    }
    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = DriveMaster.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        java.io.File tokenDir = getTokenPath();
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokenDir))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
        //returns an authorized Credential object.
        return credential;
    }

    static void debug(String msg) {
        if (debug) System.out.println(msg);
    }
    static void log(String msg) {
        if ( !quiet && ( verbose || debug )) {
            System.out.println(msg);
        }
    }

    static boolean isCommand(String arg) {
        if ( commands.contains(arg) ) { return true; }
        for ( String cmd : commands ) {
            if ( arg.startsWith(cmd) ) { return true; }
        }
        return false;
    }

    /**
     * This latest version goes through all the args in order, allowing command and options to be in any order.
     * The last one or two arguments need to be source and optional destination paths.
     * (Actually, the only requirement is that source comes before the optional destination.)
     * @param args
     */
    protected static void z_parseArgs(String... args) {
        if ( args.length == 0 ||
                args.length == 1 && ! ("--auth".equalsIgnoreCase(args[0]) || "auth".equalsIgnoreCase(args[0]))) {
            usage(0);
        }
        for ( int pos = 0; pos < args.length; pos++ ) {
            String arg = args[pos];
            if (isCommand(arg)) {
                if (cmd == null) {
                    cmd = CMD.forArg(arg);
                    continue;
                } else {
                    throw new IllegalArgumentException("Two commands specified: " +
                            cmd + " and " + CMD.forArg(arg.toUpperCase()));
                }
            } else if (helpArgs.contains(arg.toLowerCase())) {
                usage(0);
            }
            String arglc = arg.toLowerCase(); // Will screw up capitalized options.
            if ("-R".equals(arg) || "--root".equals(arglc)) {
                if ( args.length <= pos ) {
                    throw new IllegalArgumentException("Missing required argument: -R");
                }
                root_drive = args[++pos];
                if ( "mydrive".equals(root_drive.replaceAll(" ","").toLowerCase()) ) {
                    root_drive = "root";
                }
            } else if ("--skip-root".equals(arg)) {
                skipRoot = true;
                // TODO: currently recursion is always on...
//            if ("-r".equals(arg) || "--recurse".equals(arglc)) {
//                recurse = true;
//            } else if ("-i".equals(arg) || "--ignore-mime".equals(arglc)) {
//                IGNORE_MIME = true;
            } else if ("-p".equals(arg) || "--preserve".equals(arglc)) { // up/download new version if exists
                preserve = true;
            } else if ("-o".equals(arg) || "--replace".equals(arglc)) { // replace file
                replace = true;
            } else if ("-k".equals(arg) || "--keep".equals(arglc)) { // keep existing; do not up/download
                keep_original = true;
            } else if ("-x".equals(arg) || "--dry-run".equals(arglc)) {
                    doit = false;
            } else if ("-d".equals(arg) || "--debug".equals(arglc)) {
                debug = true;
            } else if ("-v".equals(arg) || "--verbose".equals(arglc)) {
                verbose = true;
            } else if ("-y".equals(arg) || "--yes".equals(arglc)) {
                YES = true;
            } else if ("-q".equals(arg) || "--quiet".equals(arglc)) {
                quiet = true;
            } else if (helpArgs.contains(arglc)) {
                usage(0);
            } else if ( ! setPath( arg )) {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if ( cmd == null ) {
            throw new IllegalArgumentException("No command specified.");
        }
        if ( CMD.AUTH.equals(cmd)) {
            return;
        }
        if (fromFilePath == null) {
            throw new IllegalArgumentException("No from file path specified.");
        }
        if ( toFilePath == null ) {
            toFilePath = "";
        }
        if ((fromFilePath.startsWith("-")) ||
                (toFilePath != null && toFilePath.startsWith("-"))) {
            throw new IllegalArgumentException("Invalid flag: " + toFilePath);
        }
        if (toFilePath != null && toFilePath.startsWith("-")) {
            throw new IllegalArgumentException("Invalid flag: " + toFilePath);
        }
    }

    private static boolean setPath(String arg) {
        if ( fromFilePath == null ) {
            fromFilePath = arg;
            return true;
        } else if ( toFilePath == null ) {
            toFilePath = arg;
            return true;
        }
        return false;
    }

    static void usage(Integer exitValue) {
         usage(null, exitValue);
    }

    static void usage(String errorMsg, Integer exitValue) {
         if ( ! StringUtils.emptyOrNull(errorMsg)) {
             System.out.println("**** " + errorMsg);
         }
         /*
         recurse || dont_recurse ?
         verbose
         quiet
         replace - replace target file if exists
         preserve - create new version if target file exists
                  - upload creates new instance of same name
                  - download creates a <name>_#.<ext> file
         keep   - do not copy file if target file exists
         dry-run - only go through the motions.
         old stuff:
//        System.out.println("\t  Commands are: up[load] | down[load] | list | --auth");
//        System.out.println("\t\t- NOTE: Recursion is not currently supported.");
//        System.out.println("\t\t- upload or download file_path to file_name in current directory, or Drive home");
//        System.out.println("  OR\tDriveMaster <cmd> -f <from_file> [-t <to_file_path>]"); //  or [-d <dest_dir>]"); // [options]  or [-d <dest_dir>]");
//        System.out.println("  OR\tDriveMaster <cmd> -f <from_file> [-t <to_file_path>] or [-d <dest_dir>]"); // [options]  or [-d <dest_dir>]");
//        System.out.println("\t\t- upload or download from_file to to_file_path"); //  OR into destination directory");
//        System.out.println("  OR\tDriveMaster <cmd> -F <from_dir>  [-d <dest_dir>]");
//        System.out.println("\t\t- upload or download all files from a directory into dest_dir");
//        System.out.println("\t\t- If dest_dir is not specified, then files will be put in the current working directory");
//        System.out.println("\t\t- which is the users \"Home\" directory on GDrive when uploading.");
          */
        System.out.println("\nDriveMaster: upload or download files to / from Google Drive");
        System.out.println("  usage:\tDriveMaster (up[load] or down[load]) [options] <from_file_or_dir> [<to_file_or_dir>] "); // [options]
        System.out.println("\t\t- upload or download files or directory contents to or from your Google Drive.");
        System.out.println("  OR\tDriveMaster list [<folder_path>]");
        System.out.println("\t\t- List contents of a drive folder. Defaults to Home.");
        System.out.println("  OR\tDriveMaster --auth");
        System.out.println("\t\t- Run the authorization process. See note below.");
        System.out.println();
        System.out.println("Options:");
//        System.out.println("\t -R | --recurse : Copy directories recursively."); // Currently the default.
        System.out.println("\t -R | --root <drive_name> : Specify root drive.  Defaults to 'My Drive'." +
                         "\n\t                  - Used to specify a folders under a Shared Drive.");
        System.out.println("\t --skip-root      : Does not force searches to be under root folder.");
        System.out.println("\t -p | --preserve  : Preserve existing target file if exists." +
                         "\n\t                  - Creates a new version of the file instead." +
                         "\n\t                  - On upload, this creates a new instance of the file with the same target name." +
                         "\n\t                  - On download, this creates a new file with the a versioned name as <name>_#.<ext>");
        System.out.println("\t -o | --overwrite : Overwrite (replace) existing target file if exists.");
        System.out.println("\t -k | --keep     : Do not copy file if target file exists.\n");
        System.out.println("\t                 : If none of preserve, overwrite, or keep are specified and a target file exists,");
        System.out.println("\t                 : the user will be prompted.\n");
        System.out.println("\t -v | --verbose  : Verbose output.");
        System.out.println("\t -d | --debug    : More verbose output.");
        System.out.println("\t -q | --quiet    : Produce no non-error output.");
//        System.out.println("\t -x | --dry-run : List files to be copied, but do not copy them. >>>>> NOT YET IMPLEMENTED. <<<<< ");
        System.out.println();
        System.out.println("Before you can use this software, you must create an access token in the drive_tokens directory.");
        System.out.println("Use --auth command to create access token.");
        System.out.println();
        System.out.println("*** To create access token, this must be run on a machine with a Browser,");
        System.out.println("    and you must be logged in to your NOAA Gmail account in that browser.");
        System.out.println();
        System.out.println("*** If you get an error saying access is restricted, copy and paste the ");
        System.out.println("    URL from the console into the Browser in which you are logged in to gmail.");

        if ( exitValue != null ) System.exit(exitValue);
    }

    public static String getUserResponse(String prompt) {
        System.out.print(prompt + " ");
        try {
            String response = new BufferedReader(new InputStreamReader(System.in)).readLine();
            return response;
        } catch (Throwable iox) {
            System.err.println("Error reading user input: " + iox);
            System.exit(-1);
        }
        return null;  // won't get here. I don't know what IntelliJ's problem is.
    }

    public static void main(String... args) throws IOException, GeneralSecurityException {
        int result = 0;
        try {
            z_parseArgs(args);
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            if (cmd == CMD.AUTH || doAuth ) {
                Credential creds = doAuth(HTTP_TRANSPORT);
                if (creds == null) {
                    usage("Failed to create token.", 404);
                }
                if ( !doAuth ) {
                    System.exit(0);
                }
            }
            // Build a new authorized API client service.
            Credential credentials = getCredentials(HTTP_TRANSPORT);
            Drive driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
//            Sheets sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credentials)
//                    .setApplicationName(APPLICATION_NAME)
//                    .build();
            switch (cmd) {
                case UP:
                    doUpload(driveService);
                    break;
                case DOWN:
                    doDownload(driveService);
                    break;
                case LIST:
                    listFolder(driveService);
                    break;
                case AUTH:
                    break;
//                default:
//                    throw new IllegalArgumentException("Bad command:"+cmd);
            }
            if ( !doit && cmd.equals(CMD.UP)) {
                for (int i = createdFolders.size()-1; i >= 0; i--) {
                    File folder = createdFolders.get(i);
                    driveService.files().delete(folder.getId()).execute();
                }
            }
        } catch (IllegalStateException isx) {
            System.err.println("ERROR: " + isx.getMessage());
            if ( debug ) {
                isx.printStackTrace(System.err);
            }
            System.exit(-1);
        } catch (IllegalArgumentException iax) {
            if ( debug ) {
                iax.printStackTrace(System.err);
            }
            System.err.println("Arguments: " + Arrays.asList(args));
            usage("ERROR: " + iax.getMessage(), -3);
        } catch (TokenResponseException itrx) {
           usage("Token error.  Please recreate your token.  See usage. ", -5);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(500);
        }
    }

    private static Credential doAuth(NetHttpTransport HTTP_TRANSPORT ) throws IOException {
        java.io.File tokenDir = getTokenPath();
        java.io.File token = new java.io.File(tokenDir, "StoredCredential");
        java.io.File backup = new java.io.File(tokenDir, "StoredCredential.bak");
        if ( token.exists()) {
            token.renameTo(backup);
            log("Prior token moved to StoredCredential.bak");
        }
        Credential creds = getCredentials(HTTP_TRANSPORT);
        return creds;
    }

    private static void doUpload(Drive service) throws Exception {
        log("Running DriveMaster to upload " + cmd.from() + fromFilePath
                + cmd.movement() + ( ! toFilePath.isEmpty() ? cmd.to() + toFilePath : "drive"));
        java.io.File fromFile = new java.io.File(fromFilePath);
        if ( !fromFile.exists()) {
            throw new FileNotFoundException(fromFilePath + " not found");
        }
        boolean dirUpload = fromFile.isDirectory();
        boolean targetIsDir = dirUpload || toFilePath.endsWith(SLASH);
        boolean skipFromDir = ( dirUpload && fromFilePath.matches(FOLDER_CONTENTS_REGEX)) ? true : false;
        boolean isShared = toFilePath.startsWith(SHARED_INDICATOR);
        if ( isShared ) {
            toFilePath = toFilePath.substring(SHARED_INDICATOR.length());
        }

        long t_start = System.nanoTime();
//        File root = getRoot(service);
        String rootID = getRootId(service);
        long t_root = System.nanoTime();
//        File parentFolder = root;
        File parentFolder = null;
        String parentId = rootID != null ? rootID : null;
        toFilePath = toFilePath.replaceAll(SLASH+"+", SLASH );
        // root slash not necessary on upload to drive.
        while (toFilePath.startsWith(SLASH)) {
            toFilePath = toFilePath.substring(1);
        }

        String toFileName = null;
        String targetPath = "";
        // find target directory
        if (dirUpload || toFilePath.contains(SLASH)) {
            String[] paths = toFilePath.split(SLASH);
            int pathAdjustment = targetIsDir ? 0 : 1;
            int i = 0;
            for ( ; i < paths.length - pathAdjustment; i++) {  //  - fileCorrection
                String pathPart = paths[i];
                boolean createFolder = i < paths.length - 1
                                        || dirUpload
                                        || toFilePath.endsWith(SLASH);
                File pathFolder = getDriveFolder(pathPart, isShared, parentFolder, targetPath, service, createFolder);
                if (pathFolder != null) {
                    targetPath += SLASH + pathPart;
                    parentId = pathFolder.getId();
                    parentFolder = pathFolder;
                    isShared = false; // ??? maybe ?
                } else if (createFolder) {
                    throw new IllegalStateException("Failed to create drive folder for component: " + pathPart);
                } else {
                    toFileName = pathPart;
                }
            }
            if ( i > 0 && i < paths.length ) {
                String lastPath = paths[paths.length - 1];
                File lastPathFile = getDriveFile(lastPath,parentId,isShared,service);
                if ( lastPathFile != null && isDriveFolder(lastPathFile,service)) {
                    targetPath += SLASH + lastPath;
                    parentFolder = lastPathFile;
                    parentId = parentFolder.getId();
                    toFileName = "";
                } else {
                    toFileName = lastPath;
                }
            }
        } else {
            toFileName = toFilePath;
        }
        if ( skipFromDir ) {
            for (java.io.File file : fromFile.listFiles()) {
                uploadFiles(file, null, parentFolder, isShared, targetPath, service);
            }
        } else {
            uploadFiles(fromFile, toFileName, parentFolder, isShared, targetPath, service);
        }
    }

    private static void uploadFiles(java.io.File fileToUpload,
                                    String toFileName,
                                    File parentFolder,
                                    boolean isShared,
                                    String targetPath,
                                    Drive service) throws IOException {
//        String parentFolderId = parentFolder.getId();
        if ( fileToUpload.isDirectory()) { //  && recurse) {
            String subFolderName = fileToUpload.getName();
            File subFolder = getDriveFolder(subFolderName, isShared, parentFolder, targetPath, service, true);
            targetPath += SLASH + subFolderName;
            if ( recurse ) {
                for (java.io.File child : fileToUpload.listFiles()) {
                    uploadFiles_(child, null, subFolder, isShared, targetPath, service);
                }
            } else {
                log("Recursion not set.  Skipping " + fileToUpload);
            }
        } else {
            String parentPath = parentFolder != null ? parentFolder.getName() : "";
            uploadFile(fileToUpload, toFileName, parentFolder, isShared, parentPath, service);
        }
    }
    private static void uploadFiles_(java.io.File fileToUpload,
                                    String toFileName,
                                    File parentFolder,
                                    boolean isShared,
                                    String drivePath,
                                    Drive service) throws IOException {
        if ( fileToUpload.isDirectory()) {
            if ( recurse ) {
                String targetName = fileToUpload.getName();
                File subFolder = getDriveFolder(targetName, isShared, parentFolder, drivePath + SLASH + targetName, service, true);
                String subFolderId = subFolder.getId();
                isShared = false;
                for (java.io.File child : fileToUpload.listFiles()) {
                    uploadFiles_(child, null, subFolder, isShared, subFolderId, service);
                }
            } else {
                log("Recursion not set. Skipping directory " + fileToUpload.getName());
            }
        } else {
            uploadFile(fileToUpload, toFileName, parentFolder, isShared, drivePath, service);
        }
    }

    private static EXISTING_OPTIONS getUserExistgFileChoice(String sourceFileName, String destFileName) {
        System.out.println("Target file " + destFileName + " for file " + sourceFileName + " exists.");
        System.out.println("\tWould you like to 1. Replace file?");
        System.out.println("\t                  2. Keep existing file and create a new version?");
        System.out.println("\t                  3. Keep existing file and continue without " +
                                                ( cmd.equals(CMD.UP) ? "up" : "down" ) + "loading file?");
        System.out.println("\t                  q. Exit the program?");
        EXISTING_OPTIONS option = null;
        do {
            String response = getUserResponse("Please select an option (123q): ");
            char answer = response.toLowerCase().charAt(0);
            switch (answer) {
                case '1':
                    option = EXISTING_OPTIONS.REPLACE;
                    break;
                case '2':
                    option = EXISTING_OPTIONS.PRESERVE;
                    break;
                case '3':
                    option = EXISTING_OPTIONS.KEEP;
                    break;
                case 'q':
                    log("Exiting.");
                    System.exit(1);
                    break;
                default:
                    continue;
            }
        } while (option == null);
        return option;
    }

    private static void uploadFile(java.io.File fileToUpload,
                                    String toFileName,
                                    File driveFolder,
                                    boolean isShared,
                                    String driveFolderPath,
                                    Drive service) throws IOException {
        String driveFolderId = driveFolder != null ? driveFolder.getId() : null;
        String toFolderDisplayPath = isShared ? SHARED_INDICATOR + toFileName : driveFolderPath;
        String targetFileName = StringUtils.emptyOrNull(toFileName) ?
                fileToUpload.getName() :
                toFileName;
        String driveFilePath = isShared ? toFolderDisplayPath : driveFolderPath+SLASH+targetFileName;
        debug("Looking for drive file : " + driveFilePath);
        File driveFile = getDriveFile(targetFileName, driveFolderId, isShared, service);
        if ( debug ) System.out.println("Found existing file: " + driveFile);
        boolean isFolder = isDriveFolder(driveFile,service);
        targetFileName = isFolder ? fileToUpload.getName() : targetFileName; // driveFile.getName();
        if ( isFolder ) {
            driveFolderId = driveFile.getId();
            toFolderDisplayPath = toFolderDisplayPath + SLASH + driveFile.getName();
            driveFile = getDriveFile(targetFileName, driveFolderId, isShared, service);
        }
        log("Uploading file " + fileToUpload.getName() + " to " + targetFileName + " in " + toFolderDisplayPath + " folder.");
        EXISTING_OPTIONS option = null;
        if ( driveFile != null && ! isFolder && ! (preserve || replace || keep_original)) {
            option = getUserExistgFileChoice(fileToUpload.getPath(), driveFilePath);
        }
        String mimeType = getMimeType(fileToUpload);
        FileContent mediaContent = new FileContent(mimeType, fileToUpload);
        File uploadedFile = null;
        if ( driveFile == null || isFolder || preserve || option == EXISTING_OPTIONS.PRESERVE ) { // new file or keep existing and upload new.
            File dummyFile = new File()
                    .setName(targetFileName)
                    .setDriveId("0AHEC6CUD0Nc5Uk9PVA")
                    .setMimeType(mimeType)
                    .setParents(Arrays.asList(new String[]{driveFolderId}));
            if ( doit ) {
                if ( driveFile != null && preserve || option == EXISTING_OPTIONS.PRESERVE ) {
                    log("Drive file " + targetFileName + " exists.  Uploading a new version.");
                }
                uploadedFile = service.files().create(dummyFile, mediaContent)
                        .setSupportsAllDrives(true)
                        .setFields("id, name, kind, mimeType, md5Checksum, parents, owners")
                        .execute();
            } else {
                System.out.println("Dry-run: upload of " + fileToUpload.getPath() + " to " + driveFilePath);
            }
        } else if ( keep_original || option == EXISTING_OPTIONS.KEEP ) {
            log("Drive file " + targetFileName + " exists and keep_original is set.  Not uploading file " + fileToUpload);
            log("Use -o to replace existing file or -p to preserve existing file and upload a new version.");
            log("See usage()");
            if ( !doit ) {
                System.out.println("Dry-run: existing drive file " + driveFilePath + " kept." );
            }
        } else {
            if ( doit ) {
                log("Drive file " + targetFileName + " exists.  Replacing with a new version.");
                uploadedFile = service.files().update(driveFile.getId(), null, mediaContent)   // .create(uploadedFile, mediaContent)
                        .setFields("*")
//                    .setFields("id, name, kind, mimeType, createdTime, modifiedTime, md5Checksum, parents, owners")
                        .execute();
            } else {
                System.out.println("Dry-run: upload of " + fileToUpload.getPath() + " to new version " + driveFilePath);
            }
        }
        if ( debug ) System.out.println("uploadedFile: " + uploadedFile);
    }

    private static File _getDriveFile(String fileName,
                                      String parentFolderId,
                                      Drive service) throws IOException {
        String parentId = parentFolderId != null ? parentFolderId : getRootId(service);
        String nextPageToken = null;
        do {
            String q = " name = '" + fileName + "' and '" + parentId + "' in parents and trashed=false";
            FileList dirs = service.files().list()
                    .setOrderBy("name")
                    .setPageToken(nextPageToken)
                    .setPageSize(100)
                    .setCorpora("user")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setQ(q)
                    .setFields("nextPageToken, files(*)")
                    .execute();
            List<File> files = dirs.getFiles();
            nextPageToken = dirs.getNextPageToken();
            for (File f : files) {
                if ( f.getName().equals(fileName)) {
                    return f;
                }
            }
        } while (nextPageToken != null);
        return null;
    }

    private static File getDriveFile(String fileName,
                                             String parentFolderId,
                                             boolean shared,
                                             Drive service) throws IOException {
        // see https://developers.google.com/drive/api/guides/ref-search-terms#operators
        StringBuilder qb = new StringBuilder()
                .append(" name = '" + fileName + "'")
                .append(" and trashed=false");
        if ( shared ) {
            // see https://stackoverflow.com/questions/28500889/how-to-get-shared-with-me-files-in-google-drive-via-api
            qb.append(" and sharedWithMe ");
        } else if ( parentFolderId != null ) {
            qb.append(" and '").append(parentFolderId).append("' in parents");
        }
        String q = qb.toString();
        String driveId = null; // "0AHEC6CUD0Nc5Uk9PVA";
        String corpora = "user"; // "drive", "allDrives"
        Files.List listFiles = service.files().list()
                .setCorpora(corpora) // "user")
//                .setSpaces("drive")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
//                .setSupportsTeamDrives(true)      // deprecated
//                .setIncludeTeamDriveItems(true)	// deprecated
                .setQ(q.toString())
                .setFields("nextPageToken, files(*)");
//                .setFields("nextPageToken, files(id, name, kind, mimeType, createdTime, modifiedTime, shortcutDetails, owners, parents)")
//                .execute();
        if ( driveId != null ) {
            listFiles.setDriveId(driveId);
        }
        FileList filesList = listFiles.execute();
        List<File> files = filesList.getFiles();
        int idx = 0;
        if ( files.size() == 0 ) {
            return null;
        } else if ( files.size() > 1 ) {
            log("More than one drive file found for " + fileName);
        }
        if (files.get(0).getMimeType().equals(SHORTCUT_MIME_TYPE)) {
            return getDriveShortcutFile(files.get(0).getShortcutDetails().getTargetId(),service);
        } else {
            File file = files.get(0);
            debug("Found drive file " + file);
            return file;
        }
    }
    private static File getDriveShortcutFile(String fileId, Drive service) throws IOException {
        File file = service.files().get(fileId)
//                .setCorpora("user")
//                .setQ(" id = '" + fileId + "'" +
//                        " and trashed=false")
                .setFields("id, name, kind, mimeType, shortcutDetails, owners, parents")
                .execute();
        if (file.getMimeType().equals(SHORTCUT_MIME_TYPE)) {
            return getDriveShortcutFile(file.getShortcutDetails().getTargetId(),service);
        } else {
            return file;
        }
    }
//    private static File getDriveFolder(String folderPath,
//                                       File parentFolder,
//                                       Drive service,
//                                       boolean createFolder) throws IOException {
//        if ( StringUtils.emptyOrNull(folderPath)) {
//            return parentFolder;
//        }
//        String[] paths = folderPath.split(SLASH);
//        for (String pathPart : paths) {
//            parentFolder = getDriveFolder(pathPart, parentFolder, service, createFolder);
//        }
//        return parentFolder;
//    }
//    private static File getDriveFolder(String folderName,
//                                   File parentFolder,
//                                   String parentFolderPath,
//                                   Drive service,
//                                   boolean createFolder) throws IOException {
//        return getDriveFolder(folderName, false, parentFolder, parentFolderPath, service, createFolder);
//    }
    private static File getDriveFolder(String folderName,
                                       boolean shared,
                                       File parentFolder,
                                       String parentFolderPath,
                                       Drive service,
                                       boolean createFolder) throws IOException {
        String parentFolderId = parentFolder != null ?
                                parentFolder.getId() :
                                null;
        File driveFile = getDriveFile(folderName, parentFolderId, shared, service);
        if ( driveFile == null ) {
            if ( createFolder ) {
                driveFile = createDriveFolder(folderName, parentFolderId, service);
            } else {
                return null;
            }
        } else if ( driveFile.getMimeType().equals(SHORTCUT_MIME_TYPE) ) {
            String targetId = driveFile.getShortcutDetails().getTargetId();
            File targetFolder = service.files().get(targetId).execute();
            if ( targetFolder == null || !targetFolder.getMimeType().equals(FOLDER_MIME_TYPE) ) {
                throw new IllegalStateException("link target is not a folder: " + folderName);
            }
            driveFile = targetFolder;
        } else if ( ! driveFile.getMimeType().equals(FOLDER_MIME_TYPE)) {
            log("Folder path element " + folderName + " is not a folder.");
            if ( createFolder ) {
                throw new IllegalStateException("Folder path is not a folder: " + folderName);
            } else {
                return null;
            }
        }
        return driveFile;
    }

    private static File createDriveFolder(String path, String parentId, Drive service) throws IOException {
        File folder = new File()
                .setName(path)
                .setMimeType("application/vnd.google-apps.folder")
                .setParents(Arrays.asList(new String[] { parentId }));
        File driveFolder = service.files().create(folder).execute();
        if ( !doit ) {
            System.out.println("Dry-run: creating drive folder " + path);
        }
        createdFolders.add(driveFolder);
        return driveFolder;
    }

    private static String getRootId(Drive service) throws IOException {
        if ( skipRoot ) {
            return null;
        }
        if ( ! "root".equals(root_drive)) {
            Drive.Drives.List list = service.drives().list();
            StringBuilder qb = new StringBuilder()
                    .append(" name = '" + root_drive + "'");
            String q = qb.toString();
            String corpora = "allDrives";
            list.set("supportsAllDrives", true);
            list.set("corpora", corpora);
            list.setQ(qb.toString());
            list.setFields("*");
            DriveList driveList = list.execute();
            List<com.google.api.services.drive.model.Drive> drives = driveList.getDrives();
            if ( drives == null || drives.size() == 0 ) {
                log("No Drives found for " + root_drive);
                throw new IllegalStateException("No Drives found for " + root_drive);
            } else if( drives.size() > 1 ) {
                log("More than one Drive found for " + root_drive);
                throw new IllegalStateException("More that one Drive found for " + root_drive);
            } else {
                com.google.api.services.drive.model.Drive drive = drives.get(0);
                return drive.getId();
            }
        } else {
            Drive.Files.Get get = service.files().get(root_drive).setFields("*");
            File root = get.execute();
            return root.getId();
        }
    }
    private static File _getRoot(Drive service) throws IOException {
        if ( skipRoot ) {
            return null;
        }
        File root = null;
        if ( ! "root".equals(root_drive)) {
            Drive.Drives.List list = service.drives().list();
            StringBuilder qb = new StringBuilder()
                    .append(" name = '" + root_drive + "'");
            String q = qb.toString();
            String corpora = "allDrives";
            list.set("supportsAllDrives", true);
            list.set("corpora", corpora);
            list.setQ(qb.toString());
            list.setFields("*");
            DriveList driveList = list.execute();
            List<com.google.api.services.drive.model.Drive> drives = driveList.getDrives();
            if ( drives != null && drives.size() > 0 ) {
                com.google.api.services.drive.model.Drive drive = drives.get(0);
                return null;
            }
        } else {
            Drive.Files.Get get = service.files().get(root_drive).setFields("*");
            root = get.execute();
        }
        return root;
    }

    private static String getMimeType(java.io.File file) { // throws Exception {
//        TikaConfig config = new TikaConfig("oap/src/tika-config.xml");
        try {
            InputStream configStream = DriveMaster.class.getResourceAsStream("/tika-config.xml");
            TikaConfig config = new TikaConfig(configStream);
            Tika tika = new Tika(config);
            String type = tika.detect(file);
            return type;
        } catch (Exception exception) {
            if ( !quiet) System.err.println("Error detecting file mime type: "+ exception);
            return "application/unknown";
        }
    }

    private static void doDownload(Drive service) throws Exception {
        if ( toFilePath != null ) {
            toFilePath = toFilePath.replaceAll(SLASH+"+", SLASH).trim();
        }
        fromFilePath = fromFilePath.replaceAll(SLASH+"+", SLASH).trim();
        boolean isShared = fromFilePath.startsWith(SHARED_INDICATOR);
        if ( isShared ) {
            fromFilePath = fromFilePath.substring(SHARED_INDICATOR.length());
        }
        while ( fromFilePath.startsWith(SLASH)) {
            fromFilePath = fromFilePath.substring(1);
        }
        log("Running DriveMaster to copy" + cmd.from() + fromFilePath
                + cmd.movement() + cmd.to() + toFilePath);
//        String drivePath = idx > 0 ? fromFilePath.substring(0,idx) : "";
        String[] dirList = new String[0];
        if ( fromFilePath.indexOf(SLASH) >= 0) {
            dirList = fromFilePath.split(SLASH);
        } else if ( ! fromFilePath.isEmpty() ){
            dirList = new String[] { fromFilePath };
        }

        String rootID = getRootId(service);
        File parent = null;
        String parentId = rootID != null ? rootID : null;
        String drivePath = "";
        for ( int i = 0; i < dirList.length-1 ; i+=1 ) {
            String dir = dirList[i];
            drivePath += SLASH + dir;
            parent = getDriveFolder(dir, isShared, parent, drivePath, service, false);
            if ( parent == null ) {
                throw new IllegalStateException("Folder not found: "+ drivePath);
            }
            parentId = parent.getId();
            isShared = false;
        }
        String driveFileName = dirList[dirList.length-1];
        File fromFile = getDriveFile(driveFileName, parentId, isShared, service);
//        File fromFile = _getDriveFile(driveFileName, parentId, service);
        if ( debug ) System.out.println("Drive file: " + fromFile);
        if ( fromFile == null ) {
            log("No drive file found: " + driveFileName);
            System.exit(-1);
        }
        boolean isFromDir = isDriveFolder(fromFile, service); // fromFile.getMimeType().equalsIgnoreCase(FOLDER_MIME_TYPE);

        java.io.File toPath = ! StringUtils.emptyOrNull(toFilePath) ?
                                new java.io.File(toFilePath) :
                                null;
        boolean isToDir = isFromDir || toPath != null && toPath.isDirectory() || toFilePath.endsWith(SLASH);
        if ( isToDir && toPath != null && ! toPath.exists()) {
            if ( doit ) {
                if (!toPath.mkdirs()) {
                    throw new IllegalStateException("Failed to make target directory " + toPath.getAbsolutePath());
                }
            } else {
                System.out.println("Dry-run: create local directory " + toPath.getPath());
            }
        }
        if ( isFromDir ) {
            if ( ! fromFilePath.matches(FOLDER_CONTENTS_REGEX)) {
                // XXX Not sure about this.  Wouldn't it have been created above?
                toPath = new java.io.File(toPath, fromFile.getName());
                if ( doit ) {
                    if (!toPath.exists()) {
                        if (!toPath.mkdirs()) {
                            throw new IllegalStateException("Failed to make directory " + toPath.getAbsolutePath());
                        }
                    }
                } else {
                    System.out.println("Dry-run: create local directory " + toPath.getPath());
                }
            }
            downloadFolder(service, fromFile, drivePath, toPath);
        } else {
            if ( toPath == null ) {
                toPath = new java.io.File(fromFile.getName());
            }
            java.io.File toDir = isToDir ? toPath : toPath.getParentFile();
            String fileName = isToDir ? driveFileName : toPath.getName();
            downloadFile(service, fromFile, drivePath, toDir, fileName);
        }
    }

    private static final String EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static void exportTo(String realFileId, java.io.File toFile,
                                 String mime_type, Drive service)
        throws Exception
    {
        try ( ByteArrayOutputStream bos = doExport(realFileId, EXCEL_MIME_TYPE, service);
              FileOutputStream fw = new FileOutputStream(toFile); )
        {
            fw.write(bos.toByteArray());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static ByteArrayOutputStream doExport(String realFileId, String mimeType,
                                                  Drive service)
        throws Exception
    {
        OutputStream outputStream = new ByteArrayOutputStream();
        try {
            service.files().export(realFileId, mimeType)
                    .executeMediaAndDownloadTo(outputStream);

            return (ByteArrayOutputStream) outputStream;
        } catch (GoogleJsonResponseException e) {
            // TODO(developer) - handle error appropriately
            System.err.println("Unable to export file: " + e.getDetails());
            throw e;
        }
    }

    private static boolean isDriveFolder(File driveFile, Drive service) throws IOException {
        if ( driveFile == null ) { return false; }
        String mimeType = driveFile.getMimeType();
        if ( FOLDER_MIME_TYPE.equals(mimeType)) {
            return true;
        } else if ( SHORTCUT_MIME_TYPE.equals(mimeType)) {
            File linkTarget = service.files().get(driveFile.getShortcutDetails().getTargetId()).execute();
            return isDriveFolder(linkTarget, service);
        } else {
            return false;
        }
    }

    private static void downloadFolder(Drive service, File fromFolder, String parentPath, java.io.File destDir) throws IOException {
        String nextPageToken = null;
        String folderPath = parentPath+SLASH+fromFolder.getName();
        do {
            FileList resultList = getFolderFiles(service, fromFolder, nextPageToken);
            nextPageToken = resultList.getNextPageToken();
            List<File> files = resultList.getFiles();
            if (files.isEmpty()) return;
            for (File file : files) {
                if (file.getMimeType().equalsIgnoreCase(FOLDER_MIME_TYPE)) {
                    if (recurse) {
                        destDir = new java.io.File(destDir, file.getName());
                        if (!destDir.exists()) {
                            if ( doit ) {
                                if (!destDir.mkdirs()) {
                                    throw new IllegalStateException("Failed to make directory " + destDir.getAbsolutePath());
                                }
                            } else {
                                System.out.println("Dry-run: create directory " + destDir.getPath());
                            }
                        }
                        downloadFolder(service, file, folderPath, destDir);
                    } else {
                        log("*** Recursion not set.  Skipping drive folder " + folderPath+SLASH+file.getName());
                    }
                } else if (file.getMimeType().equalsIgnoreCase(SHORTCUT_MIME_TYPE)) {
                    System.out.println("Skipping shortcut: " + file.getName());
                    continue;
                } else {
                    downloadFile(service, file, folderPath, destDir, file.getName());
                }
            }
        } while (nextPageToken != null);
    }
    private static void downloadFile(Drive service, File driveFile, String driveFolderPath,
                                     java.io.File destDir, String fileName) {
        boolean export = false;
        String mimeType = null;
        String driveFileType = driveFile.getMimeType();
        if ( driveFileType.startsWith(GDRIVE_TYPE_INDICATOR) &&
             ! driveFile.getMimeType().equals(FOLDER_MIME_TYPE) && // ) {
             ! IGNORE_MIME ) {
            if ( SPREADSHEET_MIME_TYPE.equals(driveFileType)) {
                mimeType = EXCEL_MIME_TYPE;
                String response = YES ?
                                    "y" :
                                    getUserResponse("Download sheets file as Excel spreadsheet?");
                switch ( response.toLowerCase().charAt(0)) {
                    case 'y':
                       export = true;
                       break;
                    case 'n':
                        log("NOT downloading drive file " + driveFile.getName());
                        return;
                    case 'q':
                        log("Quitting");
                        System.exit(0);
                }
            } else {
                System.err.println("*** Drive file type " + driveFileType + " not supported.");
                System.err.println("*** Currently only able to export Google Sheets files.");
                System.err.println("*** Skipping drive file " + driveFile.getName());
                return;
            }
        }
        java.io.File localFile = destDir != null ?
                                    new java.io.File(destDir, fileName) :
                                    new java.io.File(fileName);
        if ( localFile.exists() ) {
            String localFileName = localFile.getName();
            log("Local file " + localFile + " already exists.");
            EXISTING_OPTIONS opt = null;
            if ( ! ( keep_original || preserve || replace )) {
                opt = getUserExistgFileChoice(driveFolderPath+SLASH+driveFile.getName(), localFile.getPath());
            }
            if ( keep_original || EXISTING_OPTIONS.KEEP.equals(opt)) {
                log("Local file " + localFile + " exists and keep file specified.");
                log("Skipping drive file: " + driveFolderPath+SLASH+driveFile.getName());
                return;
            } else if ( preserve || EXISTING_OPTIONS.PRESERVE.equals(opt)) {
                int dotidx = localFileName.lastIndexOf('.');
                int idx = dotidx > 0 ? dotidx : localFileName.length();
                String baseName = localFile.getName().substring(0, idx);
                String extension = dotidx < localFileName.length() - 1 ? localFileName.substring(idx) : "";
                java.io.File[] localFiles = getLocalCopies(localFile);
                String newFileName = baseName + "_" + String.valueOf(localFiles.length) + extension;
                localFile = new java.io.File(localFile.getParentFile(), newFileName);
                log("Preserving existing local file.");
                log("Saving to " + localFile.getName());
            }
        }
        if ( doit ) {
            try {
                if ( !export ) {
                    try (FileOutputStream fos = new FileOutputStream(localFile)) {
                        log("Downloading " + driveFile.getName() + " to " + localFile);
                        long t0 = System.nanoTime();
                        Files filesService = service.files();
                        Files.Get theFile = filesService.get(driveFile.getId());// export(file.getId(),file.getMimeType())
                        //            System.out.println("Abusive: " + theFile.isAcknowledgeAbuse());
                        theFile.setAcknowledgeAbuse(theFile.isAcknowledgeAbuse())  // XXX Should we just blindly do this?
                                .executeMediaAndDownloadTo(fos);
                        long t1 = System.nanoTime();
                        long downloadTimeMs = (int) ((t1 - t0) / 1000000);
                        log("[" + downloadTimeMs + "ms]");
                    }
                } else {
                    exportTo(driveFile.getId(), localFile, mimeType, service );
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } else {
            System.out.println("Dry-run: download of " + driveFolderPath+SLASH+driveFile.getName() + " to " + localFile.getPath());
        }
    }

    private static java.io.File[] getLocalCopies(java.io.File localFile) {
        java.io.File localDir = localFile.getParentFile() != null ?
                                localFile.getParentFile() :
                                new java.io.File(".");
        String fileName = localFile.getName();
        final String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        java.io.File[] copies = localDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(java.io.File dir, String name) {
                return name.startsWith(baseName);
            }
        });
        return copies;
    }

    private static FileList getFolderFiles(Drive service, File fromFile, String pageToken)
        throws IOException
    {
        String Q = "'"+ fromFile.getId() + "' in parents and trashed=false";
        FileList result = service.files().list()
                .setQ(Q)
                .setPageSize(20)
                .setOrderBy("name")
                .setFields("nextPageToken, files(id, name, kind, mimeType, owners, parents)")
                .setPageToken(pageToken)
                .execute();
        return result;
    }

    private static void listFolder(Drive service) throws Exception {
        log("Running DriveMaster to list drive folder " + fromFilePath);
//        File root = getRoot(service);
        String rootID = getRootId(service);
//        String user = root.getOwners().get(0).getEmailAddress();
        String drivePathName;
        List<File> files = null;
        File parent = null;
        String parentId = rootID != null ? rootID : null;
        File driveFolder = null;
        String nextPageToken = null;
        if ( ! ( StringUtils.emptyOrNull(fromFilePath)
                 || "root".equals(fromFilePath)
                 || SLASH.equals(fromFilePath))) {
            fromFilePath = fromFilePath.trim();
            boolean isShared = fromFilePath.startsWith(SHARED_INDICATOR);
            if ( isShared ) {
                fromFilePath = fromFilePath.substring(SHARED_INDICATOR.length());
            }
            while ( fromFilePath.startsWith(SLASH)) {
                fromFilePath = fromFilePath.substring(1);
            }
            String[] paths = fromFilePath.split(SLASH);
            String parentFolderPath = "";
            for (int i = 0; i < paths.length; i++) {
                driveFolder = null;
                nextPageToken = null;
                drivePathName = paths[i];
                FileList dirs = null;
                parentFolderPath += SLASH + paths[i];
                do {
                    driveFolder = getDriveFolder(drivePathName, isShared, parent, parentFolderPath, service,false);
                    if (driveFolder != null) {
                        parent = driveFolder;
                        parentId = parent.getId();
                        isShared = false;
                    }
                } while (driveFolder == null && nextPageToken != null);
                if (driveFolder == null) {
                    usage("Did not find folder " + drivePathName + " under " + parent.getName(), 404);
                }
                parentId = parent.getId();
            }
        }
        nextPageToken = null;
        do {
            FileList dirs = service.files().list()
                    .setOrderBy("name")
                    .setPageToken(nextPageToken)
                    .setPageSize(100)
                    .setCorpora("user")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setQ(" '" + parentId + "' in parents and trashed=false")
                    .setFields("nextPageToken, files(*)")
                    .execute();
            files = dirs.getFiles();
            nextPageToken = dirs.getNextPageToken();
            for (File f : files) {
                System.out.println(f.getName() + " : " + f.getMimeType());
                // owners are not always returned by list()
//                                    + " owned by " + f.getOwners().get(0).getEmailAddress()
//                                    + " mod time " + f.getModifiedTime()
//                                    + " MD5: " + f.getMd5Checksum());
            }
        } while (nextPageToken != null);
    }

    private static String dirPath(String[] dirList) {
        StringBuilder path = new StringBuilder();
        for ( String dir : dirList ) {
            path.append(dir).append(java.io.File.separator);
        }
        return path.toString();
    }

    private static String dotTimestamp() {
        String ext = new SimpleDateFormat("'.'yyyyMMdd'T'HHmmss").format(new Date());
        return ext;
    }
}
