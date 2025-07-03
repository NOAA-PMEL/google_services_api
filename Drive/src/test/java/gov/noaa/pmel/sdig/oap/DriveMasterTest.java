package gov.noaa.pmel.sdig.oap;

import gov.noaa.pmel.sdig.DriveMaster;

public class DriveMasterTest {
    // 1. file
    // 2. file -> file
    // 3. file -> path/file
    // 4. file -> dir - existing
    // 4.1 file -> dir - create : use ending slash ? or flag?
    // 5. dir -> dir
    // 5.1 dir -> file - existing file - error
    // 6. spaces in file names
    static final String[] recurseUp1 = new String[] {
            "up"
            , "-d"
//                    , "-R"
            ,"test_data/test_dir"
            ,"drive_test/testing"
    };
    static final String[] recurseUpNoFlag = new String[] { // doesn't work.
            "up"
            , "-d"
            ,"test_data"
            ,"drive_test/"
    };
    static final String[] upFileToRoot = new String[]{
            "up"
            , "-d"
            , "test_data/pngImage.png"
    };
    static final String[] upFileToExistingDirNoSlash = new String[]{
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test"
    };
    static final String[] upFileToDirNOT = new String[]{
            "up"
            , "-d"
            , "-x"
            , "test_data/pngImage.png"
    };
    static final String[] upFileToRootReplace = new String[]{
            "up"
            , "-d"
            , "-o"
            , "test_data/pngImage.png"
    };
    static final String[] fullyDisordered = new String[]{
            "-o"
            , "test_data/pngImage.png"
            , "up"
            , "drive_test/subfolder/"
            , "-d"
    };
    static final String[] upFileToDirReplaceDisordered = new String[]{
            "-o"
            , "up"
            , "-d"
            , "test_data/pngImage.png"
    };
    static final String[] upFileToSimpleFileRename = new String[] {
            "up"
            , "-d"
            , "pngImage.png"
            , "aNewPngImage.png"
    };
    static final String[] upFileToSharedDir = new String[] {
            "up"
            , "-d"
            , "pngImage.png"
            , "GOBOP/"
    };
    static final String[] upFileToMarkedSharedDir = new String[] {
            "up"
            , "-d"
            , "-p"
            , "pngImage.png"
            , "shared:GOBOP"
    };
    static final String[] upFileToMarkedSharedDirNewName = new String[] {
            "up"
            , "-d"
            , "-p"
            , "pngImage.png"
            , "shared:GOBOP/Software development/Linus testing/logo.png"
    };
    static final String[] upFileToSharedDirSkipRoot = new String[] {
            "up"
            , "-d"
            , "-p"
            , "--skip-root"
            , "pngImage.png"
            , "GOBOP Operations/Drivemaster__TEST_ONLY"
    };
    static final String[] listSharedDriveDir = new String[] {
            "list"
            , "-d"
//            , "--skip-root"
            , "Drivemaster__TEST_ONLY"
    };
    static final String[] listSharedDriveDirPath = new String[] {
            "list"
            , "-d"
            , "-p"
            , "--skip-root"
            , "GOBOP Operations/Drivemaster__TEST_ONLY"
    };
    static final String[] upFileToDirSkipRoot = new String[] {
            "up"
            , "-d"
            , "-p"
            , "--skip-root"
            , "pngImage.png"
            , "drive_test"
    };
    static final String[] upFileToSharedDriveDir = new String[] {
            "up"
            , "-d"
            , "-p"
            , "-R", "GOBOP Operations"
            , "pngImage.png"
            , "Drivemaster__TEST_ONLY"
    };
    static final String[] getSharedDriveFile = new String[] {
            "down"
            , "-d"
            , "-o"
            , "--skip-root"
            , "GOBOP Operations/Drivemaster__TEST_ONLY/pngImage.png"
    };
    static final String[] getSharedDriveSheetsFile = new String[] {
            "down"
            , "-d"
            , "--skip-root"
            , "GOBOP Operations/Drivemaster__TEST_ONLY/test_metadata.xlsx"
    };
    static final String[] getSharedFileToLocalNewName = new String[] {
            "down"
            , "-d"
            , "shared:GOBOP/Software development/Linus testing/tgz file with blanks.tgz"
            , "tgzFile.tgz"
    };
    static final String[] downloadFlagFirstArgs = new String[] {
            "-d"
            , "download"
            , "drive_test/folder with blanks/jarFile.jar"
    };
    static final String[] downloadArgs = new String[] {
            "download"
            , "-d"
            , "drive_test/folder with blanks/jarFile.jar"
    };
    static final String[] downloadSharedDirToDir = new String[] {
            "down"
            , "-d"
            , "shared:GOBOP/Software development/Linus testing"
            , "test_data/GOBOP/linus"
    };
    static final String[] upToSharedDrive = new String[] {
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "shared:Drivemaster__TEST_ONLY"
    };
    static final String[] downloadSharedDirFile = new String[] {
            "down"
            , "-d"
            , "-y"
//            , "-i"
            //, "shared:Drivemaster__TEST_ONLY/test_metadata.xlsx"
            , "Drivemaster__TEST_ONLY/test_metadata.xlsx"
//            , "test_data/GOBOP/linus"
    };
    static final String[] downloadSharedDriveFile = new String[] {
            "down"
            , "-d"
//            , "-i"
            , "test.xlsx"
    };
    static final String[] downloadSharedDirFilesToDir = new String[] {
            "down"
            , "-d"
            , "shared:GOBOP/Software development/Linus testing/"
            , "test_data/GOBOP/linus"
    };
    static final String[] upFileWithBlanks = new String[] {
            "up"
            , "-d"
            , "test_data/tgz file with blanks.tgz"
    };
    static final String[] upFileWithBlanksToFolderWithBlanks = new String[] {
            "up"
            , "-d"
            , "test_data/tgz file with blanks.tgz"
            , "drive_test/folder with blanks"
    };
    static final String[] upFileToFileRootReplace = new String[] {
            "up"
            , "-d"
            , "-o"
            , "test_data/pngImage.png"
            , "newPngImage.png"
    };
    static final String[] upFileToDirFileChangeName = new String[] {
            "up"
            , "-d"
            , "-o"
            , "test_data/pngImage.png"
            , "drive_test/newPngImage.png"
    };
    static final String[] upFileToDirFileVersion = new String[] {
            "up"
            , "-d"
            , "-p"
            , "test_data/pngImage.png"
            , "drive_test/"
    };
    static final String[] upFileToDirFile = new String[] {
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test/"
    };
    static final String[] upFileToDirFileReplace = new String[] {
            "up"
            , "-d"
            , "-o"
            , "test_data/pngImage.png"
            , "drive_test/"
    };
    static final String[] upFileToFileRootKeep = new String[] {
            "up"
            , "-d"
            , "--keep"
            , "test_data/pngImage.png"
            , "newPngImage.png"
    };
    static final String[] upFileToExistgDir = new String[] {
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test/"
    };
    static final String[] upFileToExistgDirNewName = new String[] {
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test/subfolder/newPngImage.png"
    };
    static final String[] upFileToExistgDirPath = new String[] { // not tested.  Probably doesn't work right (toFilePath not right.)
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test/subfolder"
    };
    static final String[] upFileToCreateDir = new String[] {
            "up"
            , "-d"
            , "test_data/pngImage.png"
            , "drive_test/new_dir/"
    };
    static final String[] upDirContentsToDir1 = new String[] {
            "up"
            , "-d"
            , "out/"
            ,"drive_test/in1"
    };
    static final String[] upDirContentsToDirDryRun = new String[] {
            "up"
//                    , "-R"
//                    , "-d"
            , "test_data"
            ,"drive_test/in1"
            , "-x"
    };
    static final String[] upDirContentsToDir2 = new String[] {
            "up"
            , "-d"
            ,"out/*"
            ,"in2"
    };
    static final String[] upDirToDir = new String[] {
            "up"
            , "-d"
            ,"out"
            ,"drive_test/in"
    };
    static final String[] upDirToExistgFileError = new String[] {
            "up"
            , "-d"
            ,"out"
            ,"drive_test/test_file"
    };
    static final String[] upArgs1 = new String[] {
            "up"
            , "-d"
            , "/local/data/omics/large"
    };
    static final String[] upArgs2 = new String[] {
            "up"
            , "-d"
            ,"/local/data/omics/level_one"
            , "omics"
    };
    static final String[] upArgs3 = new String[] {
            "up"
            , "-d"
            ,"/local/data/omics/small"
//            ,"/sbd_reader/bin"
            , "/drive_test/omics/sean/small"
    };
    static final String[] upArgs4 = new String[] {
            "up"
            , "-d"
            ,"/local/data/omics/medium"
            , "omics/sean"
    };
    static final String[] upArgs5 = new String[] {
            "up"
            , "-d"
            ,"/Users/kamb/workspace/oa_dashboard_test_data/badtest"
            ,"bad_bunny/baddest"
    };
    // 1. file
    // 2. file -> file
    // 3. file -> path
    // 4. file -> dir - existing
    // 4.1 file -> dir - create : use ending slash ? or flag?
    // 5. dir -> dir
    // 5.1 dir -> dir - existing file - error
    // 6. spaces in file names
    static final String[] downFile = new String[] {
            "down"
            , "-d"
            , "/drive_test//pngImage.png"
    };
    static final String[] downGDoc = new String[] {
            "down"
            , "-d"
            , "/drive_test//test spreadsheet"
    };
    static final String[] downDirToDirNoRecurse = new String[] {
            "down"
            , "-d"
            ,"drive_test/testing"
            , "down/"
    };
    static final String[] downDirToDir = new String[] {
            "down"
            , "-d"
//                    , "-R"
            ,"drive_test/testing"
            , "down/"
    };
    static final String[] downDirDryRun = new String[] {
            "down"
            , "-x"
//                    , "-R"
            ,"drive_test/testing"
    };
    static final String[] downFileToFile = new String[] {
            "down"
            , "-d"
            , "/drive_test//test.file"
            , "scratch/whatIsThis"
    };
    static final String[] downFileToDir = new String[] {
            "down"
            , "-d"
            , "/drive_test//test.file"
            , "scratch/drive/"
    };
    static final String[] downArgs5 = new String[] {
            "down"
            , "-d"
            ,"bad_bunny/baddest"
            , "scratch/bunny/bellies"
    };
    static final String[] downArgs5_1 = new String[] {
            "down"
            , "-d"
            ,"bad_bunny/baddest"
            , "scratch/1255.2011252003"
//                    "//xml/33RR/xagwt.xml"
    };
    static final String[] listArgs = new String[] {
            "list"
            , "-d"
            , "/drive_test"
//                    , "argo/OHC"
//                    , "2004"
//                    "sdis/foo"
//                    "admin"
//                    "tweb"
    };
    static final String[] listSharedFolder = new String[]{
            "list"
            , "-d"
            , "shared:Drivemaster__TEST_ONLY"
    };
    static final String[] tooManyArgs = new String[] {
            "up",
            "-d",
            "gov",
            "javaterm",
            "resources"
    };
    static final String[] badCommand = new String[] {
            "push"
            , "-d"
            , "test_data/pngImage.png"
    };
    static final String[] testArgs = new String[] {
            "list"
            , "-d"
            , "PMEL-BGC-S2A"
    };
    public static void main(String[] args) {
        System.out.println("CWD: " + new java.io.File(".").getAbsolutePath());
        // /local/data/omics
        // 2120 JV236.1_16Sv4v5_GoldZachary_S073777.1.R2.fastq.gz   "small"
        // 17592 JV236.1_16Sv4v5_GoldZachary_S073676.1.R1.fastq.gz 9005835 8.6M  "medium"
        // 61608 JV236.1_16Sv4v5_GoldZachary_S073629.1.R1.fastq.gz 31539357 30M  "large"

        String upFile = "/Users/kamb/workspace/oa_dashboard_test_data/badtest";
//        String upFile = "/Users/kamb/workspace/oa_dashboard_test_data/agwt.xml";
//        String upFile = "/local/data/omics/medium";
        try {
            testArgs[1] = testArgs[1].toLowerCase();
            String[] authArgs = new String[] {"--auth"};
            String[] altAuthArgs = new String[] { "up", "tokens.tgz", "--auth"};
            String[] none = new String[0];

            String[] debugArgs =  downloadSharedDirFile;
            DriveMaster.main(debugArgs);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
