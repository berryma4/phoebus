/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.phoebus.framework.preferences.PreferencesReader;

/** Common alarm system code
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class AlarmSystem
{
    /** Alarm system logger */
    public static final Logger logger = Logger.getLogger(AlarmSystem.class.getPackageName());

    /** Suffix for the topic where alarm server reports alarm state */
    public static final String STATE_TOPIC_SUFFIX = "State";

    /** Suffix for the topic that clients use to send commands to alarm server */
    public static final String COMMAND_TOPIC_SUFFIX = "Command";

    /** Suffix for the topic that server uses to send annunciations */
    public static final String TALK_TOPIC_SUFFIX = "Talk";

    /** Kafka Server host:port */
    public static final String server;

    /** Name of alarm tree root */
    public static final String config_name;

    /** Timeout in seconds for initial PV connection */
    public static final int connection_timeout;

    /** Item level of alarm area. A level of 2 would show all the root levels children. */
    public static final int alarm_area_level;

    /** Number of columns in the alarm area */
    public static final int alarm_area_column_count;

    /** Gap between alarm area panel items */
    public static final int alarm_area_gap;

    /** Font size for the alarm area view */
    public static final int alarm_area_font_size;

    /** Limit for the number of context menu items */
    public static final int alarm_menu_max_items;

    /** Alarm table row limit */
    public static final int alarm_table_max_rows;

    /** Directory used for executing commands */
    public static final File command_directory;

    static
    {
        final PreferencesReader prefs = new PreferencesReader(AlarmSystem.class, "/alarm_preferences.properties");
        server = prefs.get("server");
        config_name = prefs.get("config_name");
        connection_timeout = prefs.getInt("connection_timeout");
        alarm_area_level = prefs.getInt("alarm_area_level");
        alarm_area_column_count = prefs.getInt("alarm_area_column_count");
        alarm_area_gap = prefs.getInt("alarm_area_gap");
        alarm_area_font_size = prefs.getInt("alarm_area_font_size");
        alarm_menu_max_items = prefs.getInt("alarm_menu_max_items");
        alarm_table_max_rows = prefs.getInt("alarm_table_max_rows");
        command_directory = new File(replaceProperties(prefs.get("command_directory")));
    }

    /** @param value Value that might contain "$(prop)"
     *  @return Value where "$(prop)" is replaced by Java system property "prop"
     */
    private static String replaceProperties(final String value)
    {
        final Matcher matcher = Pattern.compile("\\$\\((.*)\\)").matcher(value);
        if (matcher.matches())
        {
            final String prop_name = matcher.group(1);
            final String prop = System.getProperty(prop_name);
            if (prop == null)
                logger.log(Level.SEVERE, "Alarm System settings: Property '" + prop_name + "' is not defined");
            return prop;
        }
        // Return as is
        return value;
    }
}
