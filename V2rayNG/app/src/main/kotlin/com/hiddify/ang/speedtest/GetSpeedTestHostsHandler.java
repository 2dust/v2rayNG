package com.hiddify.ang.speedtest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class GetSpeedTestHostsHandler extends Thread {

    HashMap<Integer, String> mapKey = new HashMap<>();
    HashMap<Integer, List<String>> mapValue = new HashMap<>();
    double selfLat = 0.0;
    double selfLon = 0.0;
    boolean finished = false;


    public HashMap<Integer, String> getMapKey() {
        return mapKey;
    }

    public HashMap<Integer, List<String>> getMapValue() {
        return mapValue;
    }

    public double getSelfLat() {
        return selfLat;
    }

    public double getSelfLon() {
        return selfLon;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void run() {
        //Get latitude, longitude
        try {
            URL url = new URL("https://www.speedtest.net/api/js/servers?engine=js&search=Orange&https_functional=true&limit=100");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            int code = urlConnection.getResponseCode();

            if (code == 200) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                urlConnection.getInputStream()));

                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.contains("isp=")) {
                        continue;
                    }
                    selfLat = Double.parseDouble(line.split("lat=\"")[1].split(" ")[0].replace("\"", ""));
                    selfLon = Double.parseDouble(line.split("lon=\"")[1].split(" ")[0].replace("\"", ""));
                    break;
                }

                br.close();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

        String uploadAddress = "";
        String name = "";
        String country = "";
        String cc = "";
        String sponsor = "";
        String lat = "";
        String lon = "";
        String host = "";


        //Best server
        int count = 0;
        try {
            //URL url = new URL("https://www.speedtest.net/speedtest-servers-static.php");
            URL url = new URL("https://www.speedtest.net/api/js/servers?engine=js&search=Orange&https_functional=true&limit=100");
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            int code = urlConnection.getResponseCode();

            if (code == 200) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(
                                urlConnection.getInputStream()));

                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("<server url")) {
                        uploadAddress = line.split("server url=\"")[1].split("\"")[0];
                        lat = line.split("lat=\"")[1].split("\"")[0];
                        lon = line.split("lon=\"")[1].split("\"")[0];
                        name = line.split("name=\"")[1].split("\"")[0];
                        country = line.split("country=\"")[1].split("\"")[0];
                        cc = line.split("cc=\"")[1].split("\"")[0];
                        sponsor = line.split("sponsor=\"")[1].split("\"")[0];
                        host = line.split("host=\"")[1].split("\"")[0];

                        List<String> ls = Arrays.asList(lat, lon, name, country, cc, sponsor, host);
                        mapKey.put(count, uploadAddress);
                        mapValue.put(count, ls);
                        count++;
                    }
                }

                br.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        mapKey.put(0,"http://speedtest.oopaya.com:8080/speedtest/upload.php");
        List<String> ls = Arrays.asList("0", "0", "name", "country", "cc", "sponsor", "speedtest.oopaya.com:8080");
        mapValue.put(count, ls);
        finished = true;
    }
}