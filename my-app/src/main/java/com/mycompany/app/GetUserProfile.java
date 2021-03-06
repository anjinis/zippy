/*
 * Copyright (c) 2016 Uber Technologies, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.mycompany.app;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.util.store.AbstractDataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.client.http.HttpClient;
import com.uber.sdk.rides.auth.OAuth2Credentials;
import com.uber.sdk.rides.client.CredentialsSession;
import com.uber.sdk.rides.client.SessionConfiguration;
import com.uber.sdk.rides.client.UberRidesApi;
import com.uber.sdk.rides.client.error.ApiError;
import com.uber.sdk.rides.client.error.ClientError;
import com.uber.sdk.rides.client.error.ErrorParser;
import com.uber.sdk.rides.client.model.UserProfile;
import com.uber.sdk.rides.client.services.RidesService;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Properties;

import retrofit2.Response;

/**
 * Demonstrates how to authenticate the user and load their profile via the command line.
 */
public final class GetUserProfile {

    private GetUserProfile() {}

    private static LocalServerReceiver localServerReceiver;

    public static void main(String[] args) throws Exception {
        // Create or load a credential for the user.
        SessionConfiguration config = createSessionConfiguration();
        Credential credential = authenticate(System.getProperty("user.name"), config);

      //  token = credential.getAccessToken();
        // System.out.printf("Creds; %s", credential.getAccessToken());

        HttpClient httpclient = HttpClient.createDefault();
        // HttpPost httppost = new HttpPost("api.uber.com/v1/deliveries/quote");


        System.exit(0);
    }

    /**
     * Authenticate the given user. If you are distributing an installed application, this method
     * should exist on your server so that the client ID and secret are not shared with the end
     * user.
     */
    private static Credential authenticate(String userId, SessionConfiguration config) throws Exception {
        OAuth2Credentials oAuth2Credentials = createOAuth2Credentials(config);

        // First try to load an existing Credential. If that credential is null, authenticate the user.
        Credential credential = oAuth2Credentials.loadCredential(userId);
        if (credential == null || credential.getAccessToken() == null) {
            // Send user to authorize your application.
            System.out.printf("Add the following redirect URI to your developer.uber.com application: %s%n",
                    oAuth2Credentials.getRedirectUri());
            System.out.println("Press Enter when done.");

            System.in.read();

            // Generate an authorization URL.
            String authorizationUrl = oAuth2Credentials.getAuthorizationUrl();
            System.out.printf("In your browser, navigate to: %s%n", authorizationUrl);
            System.out.println("Waiting for authentication...");

            // Wait for the authorization code.
            String authorizationCode = localServerReceiver.waitForCode();
            System.out.println("Authentication received.");

            System.out.println(authorizationCode);
            // Authenticate the user with the authorization code.
            credential = oAuth2Credentials.authenticate(authorizationCode, userId);
        }
        localServerReceiver.stop();

        return credential;
    }

    /**
     * Creates an {@link OAuth2Credentials} object that can be used by any of the servlets.
     */
    public static OAuth2Credentials createOAuth2Credentials(SessionConfiguration sessionConfiguration) throws Exception {




        // Store the users OAuth2 credentials in their home directory.
        File credentialDirectory =
                new File(System.getProperty("user.home") + File.separator + ".uber_credentials");
        credentialDirectory.setReadable(true, true);
        credentialDirectory.setWritable(true, true);
        // If you'd like to store them in memory or in a DB, any DataStoreFactory can be used.
        AbstractDataStoreFactory dataStoreFactory = new FileDataStoreFactory(credentialDirectory);

        // Build an OAuth2Credentials object with your secrets.
        return new OAuth2Credentials.Builder()
                .setCredentialDataStoreFactory(dataStoreFactory)
                .setRedirectUri(sessionConfiguration.getRedirectUri())
                .setClientSecrets(sessionConfiguration.getClientId(), sessionConfiguration.getClientSecret())
                .build();
    }

    public static SessionConfiguration createSessionConfiguration() throws Exception {
        // Load the client ID and secret from {@code resources/secrets.properties}. Ideally, your
        // secrets would not be kept local. Instead, have your server accept the redirect and return
        // you the accessToken for a userId.
        Properties secrets = loadSecretProperties();

        String clientId = secrets.getProperty("clientId");
        String clientSecret = secrets.getProperty("clientSecret");

        if (clientId.equals("INSERT_CLIENT_ID_HERE") || clientSecret.equals("INSERT_CLIENT_SECRET_HERE")) {
            throw new IllegalArgumentException(
                    "Please enter your client ID and secret in the resources/secrets.properties file.");
        }

        // Start a local server to listen for the OAuth2 redirect.
        localServerReceiver = new LocalServerReceiver.Builder().setPort(8181).build();
        String redirectUri = localServerReceiver.getRedirectUri();

        return new SessionConfiguration.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRedirectUri(redirectUri)
                .build();

    }

    /**
     * Loads the application's secrets.
     */
    private static Properties loadSecretProperties() throws Exception {
        Properties properties = new Properties();
        InputStream propertiesStream = GetUserProfile.class.getClassLoader().getResourceAsStream("secrets.properties");
        if (propertiesStream == null) {
            // Fallback to file access in the case of running from certain IDEs.
            File buildPropertiesFile = new File("src/main/resources/secrets.properties");
            if (buildPropertiesFile.exists()) {
                properties.load(new FileReader(buildPropertiesFile));
            } else {
                buildPropertiesFile = new File("samples/cmdline-sample/src/main/resources/secrets.properties");
                if (buildPropertiesFile.exists()) {
                    properties.load(new FileReader(buildPropertiesFile));
                } else {
                    throw new IllegalStateException("Could not find secrets.properties");
                }
            }
        } else {
            properties.load(propertiesStream);
        }
        return properties;
    }
}
