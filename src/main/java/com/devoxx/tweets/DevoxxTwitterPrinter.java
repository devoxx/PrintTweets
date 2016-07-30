package com.devoxx.tweets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * The Devoxx twitter printer.
 *
 * Uses the IBM BlueMix Twitter Insights REST call.
 *
 * @author Stephan Janssen
 */
public class DevoxxTwitterPrinter {

    private int counter = 0;

    private Set<String> printedIds = new HashSet<>();

    private File printedIdsFile = new File("printedIds.bin");

    public DevoxxTwitterPrinter() throws IOException {
        String url = "https://cdeservice.mybluemix.net/api/v1/messages/search?q=Devoxx&from=0&size=100";

        while (url != null) {

            JsonElement jsonTweets = readTweets(url);

            loadPrintedTweets();

            printMediaTweets(jsonTweets);

            url = getNextUrl((JsonObject) jsonTweets);
        }
    }

    private String getNextUrl(final JsonObject jsonTweets) {
        final JsonObject related = jsonTweets.get("related").getAsJsonObject();
        if (related != null) {
            final JsonObject next = related.get("next").getAsJsonObject();
            return next.get("href").getAsString();
        } else {
            return null;
        }
    }

    private JsonElement readTweets(final String url) throws IOException {

        final Document doc =
                Jsoup.connect(url)
                        .header("Authorization", "Basic " + getBase64Login())
                        .timeout(10_000)
                        .ignoreContentType(true)
                        .get();

        return new JsonParser().parse(doc.text());
    }

    /**
     * The BlueMix Twitter Insights credentials.
     * @return base64login
     */
    private String getBase64Login() {
        final String username = "18a89a22-ba35-4d79-9142-98562fb2b0e5";
        final String password = "O0UhQaHETe";
        String login = username + ":" + password;
        return new String(Base64.encodeBase64(login.getBytes()));
    }

    private void printMediaTweets(final JsonElement jsonTweets) {

        JsonArray tweetsArray = (JsonArray) ((JsonObject) jsonTweets).get("tweets");

        StreamSupport.stream(tweetsArray.spliterator(), false)
                     .filter(t -> t.toString().contains("\"media\":"))
                     .forEach(this::printTweet);
    }

    /**
     * Print a tweet.
     * @param jsonElement the tweet JSON element
     */
    private void printTweet(final JsonElement jsonElement) {

        final JsonElement message = jsonElement.getAsJsonObject().get("message");
        final JsonElement twitter_entities = message.getAsJsonObject().get("twitter_entities");
        final JsonElement media = twitter_entities.getAsJsonObject().get("media");

        if (media != null) {
            String postedTime = message.getAsJsonObject().get("postedTime").getAsString();

            String mediaUrl = ((JsonArray) media).get(0).getAsJsonObject().get("media_url").getAsString();

            final JsonElement author = message.getAsJsonObject().get("actor");
            String authorName = author.getAsJsonObject().get("displayName").getAsString();
            String authorUsername = author.getAsJsonObject().get("preferredUsername").getAsString();

            String id = message.getAsJsonObject().get("id").getAsString();
            if (!printedIds.contains(id)) {

                String value = String.format("%d) %s by %s (@%s) on %s", ++counter, mediaUrl, authorName, authorUsername, postedTime);
                System.out.println(value);

                savePrintedId(id);
            } else {
                System.out.println(id + " already printed");
            }
        }
    }

    private void savePrintedId(String id) {
        printedIds.add(id);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(printedIdsFile))) {
            oos.writeObject(printedIds);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPrintedTweets() {
        System.out.println("Loading printed tweets from "+printedIdsFile.getAbsolutePath());
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(printedIdsFile))) {
            printedIds = (HashSet<String>) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            new DevoxxTwitterPrinter();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
