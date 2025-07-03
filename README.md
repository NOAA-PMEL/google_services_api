# google_services_api
Tools and Utilities to interact with Google Workspace service APIs (ie, Drive and Sheets)

Currently provided are a "QuickStart" example for authenticating and using the Sheets API, 
as well as a fully-functioning (if still under development) "drivemaster" application
that provides upload to, download from, and listing of Google Drive folders.

To use either application, the user must have client credentials for an approved client
application, and the authentication tokens need to be created using those credentials.

To create the tokens, the application must be run interactively.
If the tokens are not present or are invalid, the application will launch
the default browser with a Google SSO prompt.
If the user is not logged into their Google account in the default browser,
then the user must either log in separately, or paste the URL provided in
the console into a browser window in the browser in which the user is logged in.

The "Access blocked: cnsd-dev-mail-test can only be used within its organization"
error message on the sign-in page indicates that the user is not logged into their
Google account in the browser being used.

Once the tokens are created, the application can be run in a non-interactive,
scripted mode.

