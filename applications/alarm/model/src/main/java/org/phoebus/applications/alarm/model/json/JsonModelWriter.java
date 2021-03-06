/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.model.json;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.phoebus.applications.alarm.client.ClientState;
import org.phoebus.applications.alarm.model.AlarmTreeItem;
import org.phoebus.applications.alarm.model.AlarmTreeLeaf;
import org.phoebus.applications.alarm.model.BasicState;
import org.phoebus.applications.alarm.model.TitleDetail;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Write alarm model as JSON
 *  @author Kay Kasemir
 */
public class JsonModelWriter
{
    // Use Jackson ObjectMapper where it can be used
    // without adding annotations to data classes,
    // or without making immutable objects mutable.
    //
    // Otherwise use streaming JsonGenerator resp. JsonParser,
    // which is faster anyway and allows JSON code to be
    // limited to this package
    public static final ObjectMapper mapper = new ObjectMapper();

    public static byte[] toJsonBytes(final BasicState state) throws Exception
    {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try
        (
            JsonGenerator jg = mapper.getFactory().createGenerator(buf);
        )
        {
            jg.writeStartObject();
            jg.writeStringField(JsonTags.SEVERITY, state.severity.name());
            if (state instanceof ClientState)
            {
                final ClientState as = (ClientState) state;
                jg.writeStringField(JsonTags.MESSAGE, as.message);
                jg.writeStringField(JsonTags.VALUE, as.value);
                {
                    jg.writeObjectFieldStart(JsonTags.TIME);
                    jg.writeNumberField(JsonTags.SECONDS, as.time.getEpochSecond());
                    jg.writeNumberField(JsonTags.NANO, as.time.getNano());
                    jg.writeEndObject();
                }
                jg.writeStringField(JsonTags.CURRENT_SEVERITY, as.current_severity.name());
                jg.writeStringField(JsonTags.CURRENT_MESSAGE, as.current_message);
            }
            jg.writeEndObject();
        }
        return buf.toByteArray();
    }

    public static String toJsonString(final AlarmTreeItem<?> item) throws Exception
    {
        return toJson(item).toString();
    }

    public static byte[] toJsonBytes(final AlarmTreeItem<?> item) throws Exception
    {
        return toJson(item).toByteArray();
    }

    private static ByteArrayOutputStream toJson(final AlarmTreeItem<?> item) throws Exception
    {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try
        (
            JsonGenerator jg = mapper.getFactory().createGenerator(buf);
        )
        {
            jg.writeStartObject();

            if (item instanceof AlarmTreeLeaf)
                writeLeafDetail(jg, (AlarmTreeLeaf) item);

            writeTitleDetail(jg, JsonTags.GUIDANCE, item.getGuidance());
            writeTitleDetail(jg, JsonTags.DISPLAYS, item.getDisplays());
            writeTitleDetail(jg, JsonTags.COMMANDS, item.getCommands());
            writeTitleDetail(jg, JsonTags.ACTIONS, item.getActions());

            jg.writeEndObject();
        }
        return buf;
    }

    private static void writeLeafDetail(final JsonGenerator jg, final AlarmTreeLeaf item) throws Exception
    {
        jg.writeStringField(JsonTags.DESCRIPTION, item.getDescription());
        if (! item.isEnabled())
            jg.writeBooleanField(JsonTags.ENABLED, false);
        if (! item.isLatching())
            jg.writeBooleanField(JsonTags.LATCHING, false);
        if (! item.isAnnunciating())
            jg.writeBooleanField(JsonTags.ANNUNCIATING, false);
        if (item.getDelay() > 0)
            jg.writeNumberField(JsonTags.DELAY, item.getDelay());
        if (item.getCount() > 0)
            jg.writeNumberField(JsonTags.COUNT, item.getCount());
        if (! item.getFilter().isEmpty())
            jg.writeStringField(JsonTags.FILTER, item.getFilter());
    }

    private static void writeTitleDetail(final JsonGenerator jg, final String name, final List<TitleDetail> infos) throws Exception
    {
        if (infos.isEmpty())
            return;

        jg.writeArrayFieldStart(name);
        for (TitleDetail info : infos)
        {
            jg.writeStartObject();
            jg.writeStringField(JsonTags.TITLE, info.title);
            jg.writeStringField(JsonTags.DETAILS, info.detail);
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }
}
