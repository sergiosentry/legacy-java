package com.example;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Properties;

import com.example.sentry.MySentryClientFactory;

import io.sentry.SentryClient;
import io.sentry.connection.LockedDownException;
import io.sentry.connection.TooManyRequestsException;
import io.sentry.dsn.Dsn;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        String dsn = null;
        
        try {
            FileReader reader = new FileReader("sentry.properties");  
      
            Properties p = new Properties();  
            p.load(reader);  
              
            dsn = p.getProperty("dsn");  
        } catch (IOException e) {
            System.out.println("Could not read properties file");
        }
        
        MySentryClientFactory factory = new MySentryClientFactory();
        SentryClient sentryClient = factory.createSentryClient(new Dsn(dsn));

        try {
            throw new UnsupportedOperationException("You should not call this");
        } catch (Exception e) {
            sentryClient.sendException(e);
        }

    }
}
