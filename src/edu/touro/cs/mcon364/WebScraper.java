package edu.touro.cs.mcon364;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScraper
{
    private static ExecutorService es = Executors.newFixedThreadPool(500);
    private static Set<String> emails = Collections.synchronizedSet(new HashSet<>());
    private static Set<String> sources = Collections.synchronizedSet(new HashSet<>(Set.of("https://www.touro.edu/")));
    private static List<TableEntry> tableEntries = Collections.synchronizedList(new ArrayList<>());

    public void addToDatabase(String url)
    {
        if (emails.size() < 10_000)
        {
            System.out.println(url);
            HtmlPage htmlPage = null;
            try (WebClient client = new WebClient()) {
                client.getOptions().setCssEnabled(false);
                client.getOptions().setJavaScriptEnabled(false);
                client.getOptions().setPrintContentOnFailingStatusCode(false);
                Logger.getLogger("com.gargoylesoftware").setLevel(Level.WARNING);
                htmlPage = client.getPage(url);
            } catch (Exception ignored) {
            }
            String pageContent = htmlPage.getWebResponse().getContentAsString();
            Pattern emailPattern = Pattern.compile("[_A-Za-z0-9-+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})");
            Matcher emailMatcher = emailPattern.matcher(pageContent);

            while (emailMatcher.find())
            {
                String email = emailMatcher.group().toLowerCase();

                Pattern badEmailPattern = Pattern.compile("((\\.jpg?)|(\\.webp)|(\\.png)|(\\.svg))$");
                Matcher badEmailMatcher = badEmailPattern.matcher(email);
                if(!(emails.contains(email)) && !(badEmailMatcher.find()))
                {
                    System.out.println(email);
                    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
                    emails.add(email);
                    TableEntry tableEntry = new TableEntry(email, url, timestamp);
                    tableEntries.add(tableEntry);
                    System.out.println(emails.size());
                }
            }
            Pattern sourcePattern = Pattern.compile("<a\\s.*href=['\"](.*?)['\"].*?>");
            Matcher sourceMatcher = sourcePattern.matcher(pageContent);
            URL absoluteUrl = null;
            while (sourceMatcher.find())
            {
                String source = sourceMatcher.group(1);
                try {
                    absoluteUrl = htmlPage.getFullyQualifiedUrl(source);
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                }
                String urlAsString = absoluteUrl.toString();
                Pattern badSourcePattern;
                badSourcePattern = Pattern.compile("^((mailto:)|(tel:)|(javascript:)|(https?://(www\\.)?((youtube\\.com)|(youtu\\.be)|(google)|(opera)|(amazon)|(linkedin)|(help\\.twitter)|(shoptouro50)|(touroscholar)|(touro\\.textbookx)|(rainn)|(ncbi\\.nlm\\.nih\\.gov/portal)|(goarmyed)|((ftp\\.)?ncbi\\.nlm\\.nih\\.gov)|(transparency-in-coverage\\.uhc\\.com))))|(\\.(pdf)|(zip)|(jpe?g)|(docx)|(xlsx)|(png)|(ris)|(mp3)|(xml))$");
                Matcher badSourceMatcher;
                badSourceMatcher = badSourcePattern.matcher(urlAsString);
                if (!(sources.contains(urlAsString)) && !(badSourceMatcher.find()))
                {
                    sources.add(urlAsString);
                    es.submit(() -> addToDatabase(urlAsString));
                }
            }
        }
        else
        {
            synchronized (this)
            {
                Map<String, String> env = System.getenv();
                String endpoint = env.get("dbendpoint");
                String user = env.get("user");
                String password = env.get("password");
                String connectionUrl = "jdbc:sqlserver://" + endpoint + ";"
                        + "database=Edelstein_Avrumy;"
                        + "username=" + user + ";"
                        + "password=" + password + ";"
                        + "encrypt=false;"
                        + "trustServerCertificate=false;"
                        + "loginTimeout=30;";
                try (Connection connection = DriverManager.getConnection(connectionUrl)) {
                    String sql = "INSERT INTO emails (emailAddress, source, timestamp) VALUES (?, ?, ?)";
                    PreparedStatement preparedStatement = connection.prepareStatement(sql);

                    int batchSize = 1000;
                    int count = 0;

                    for (TableEntry tableEntry : tableEntries) {
                        preparedStatement.setString(1, tableEntry.getEmailAddress());
                        preparedStatement.setString(2, tableEntry.getSource());
                        preparedStatement.setTimestamp(3, tableEntry.getTimestamp());

                        preparedStatement.addBatch();

                        count++;

                        if (count % batchSize == 0)
                            preparedStatement.executeBatch();
                    }
                    preparedStatement.executeBatch();
                    System.out.println("Bulk Add Complete");
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }
                System.exit(0);
            }
        }
    }
}
