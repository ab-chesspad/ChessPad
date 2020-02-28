package com.ab.pgn.fics;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Alexander Bootman on 4/23/19.
 */
public class OrderedPropertiesTest {
    @Test
    @Ignore("raptor test")
    public void testLoading() {
        OrderedProperties orderedProperties = new OrderedProperties("/home/alex/.raptor/raptor.properties");

        for (Map.Entry<String, String> entry : orderedProperties.entrySet() ) {
            System.out.println(String.format("%s=%s", entry.getKey(), entry.getValue()));
        }
    }

    @Test
    public void testRegex() {
        String[] src = {
            "defprompt 1",
            "app-quad2345-quad678-sash-weights=457,534",
            "fics-Secondary-is-anon-guest = true",
            "games-table-rated-index = 0",
            "games-table-show-atomic = ",
            "games-table-show-atomic1",
        };

        Pattern pattern = Pattern.compile("^([^=^ ]+) *=? *(.*)$");
        for(String line : src) {
            Matcher m = pattern.matcher(line);
            if(m.find()) {
                String key = m.group(1);
                String value = m.group(2);
                System.out.println(String.format("%s=%s", key, value));
            }
        }
    }
}