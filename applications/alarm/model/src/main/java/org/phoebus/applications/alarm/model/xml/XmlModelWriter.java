/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.model.xml;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.phoebus.applications.alarm.model.AlarmTreeItem;
import org.phoebus.applications.alarm.model.AlarmTreeLeaf;
import org.phoebus.applications.alarm.model.TitleDetail;
import org.phoebus.framework.persistence.IndentingXMLStreamWriter;
import org.phoebus.framework.persistence.XMLUtil;

/**
 * Writes Alarm System model to XML.
 * @author Evan Smith
 *
 */
@SuppressWarnings("nls")
public class XmlModelWriter
{
    private XMLStreamWriter writer;

    public XmlModelWriter() throws Exception
    {
        initWriter(System.out);
    }

    public XmlModelWriter(final OutputStream stream) throws Exception
    {
        initWriter(stream);
    }

    private void initWriter(final OutputStream stream) throws Exception
    {
        final XMLStreamWriter base =
                XMLOutputFactory.newInstance().createXMLStreamWriter(stream, XMLUtil.ENCODING);
            writer = new IndentingXMLStreamWriter(base);

            writer.writeStartDocument(XMLUtil.ENCODING, "1.0");
    }

    public void getModelXML(final AlarmTreeItem<?> item) throws Exception
    {
        getModelXML(item, 0);
    }

    private void getModelXML(final AlarmTreeItem<?> item, final int level) throws Exception
    {
        if (level == 0) /* Root */
        {
            writer.writeStartElement(XmlModelReader.TAG_CONFIG);
            writer.writeAttribute(XmlModelReader.TAG_NAME, item.getName());

            getItemXML(item);

            for (final AlarmTreeItem<?> child : item.getChildren())
                getModelXML(child, level+1);

            writer.writeEndElement();
            close();
        }
        else if (item instanceof AlarmTreeLeaf) /* Leaf */
        {
            final AlarmTreeLeaf leaf = (AlarmTreeLeaf) item;

            writer.writeStartElement(XmlModelReader.TAG_PV);
            writer.writeAttribute(XmlModelReader.TAG_NAME, item.getName());

            getLeafXML(leaf);

            getItemXML(item);

            writer.writeEndElement();
        }
        else /* Component */
        {
            writer.writeStartElement(XmlModelReader.TAG_COMPONENT);
            writer.writeAttribute(XmlModelReader.TAG_NAME, item.getName());

            getItemXML(item);

            for (final AlarmTreeItem<?> child : item.getChildren())
                getModelXML(child, level+1);

            writer.writeEndElement();
        }
    }

    private void getItemXML(final AlarmTreeItem<?> item) throws Exception
    {
        // Write XML for Guidance
        final List<TitleDetail> guidance = item.getGuidance();

        if (!guidance.isEmpty())
        {
            getTitleDetailListXML(guidance, XmlModelReader.TAG_GUIDANCE);
        }

        // Write XML for Displays
        final List<TitleDetail> displays = item.getDisplays();

        if (!displays.isEmpty())
        {
            getTitleDetailListXML(displays, XmlModelReader.TAG_DISPLAY);
        }

        // Write XML for Commands
        final List<TitleDetail> commands = item.getCommands();

        if (!commands.isEmpty())
        {
            getTitleDetailListXML(commands, XmlModelReader.TAG_COMMAND);
        }
        /*
         * TODO : Automated actions are not yet implemented.
        // Write XML for Actions
        final List<TitleDetail> actions = item.getActions();

        if (!actions.isEmpty())
        {
            getTitleDetailListXML(actions, XmlModelReader.TAG_ACTIONS);
        }
        */
    }

    // TODO: This will not work with automated_actions as the XML schema expects a third child "delay" to go along
    //          with "title" and "details".
    private void getTitleDetailListXML(final List<TitleDetail> tdList, final String itemSubType) throws Exception
    {
        for (final TitleDetail td : tdList)
        {
            // TODO: would a title element ever have empty or null title/detail?
            writer.writeStartElement(itemSubType);

            writer.writeStartElement(XmlModelReader.TAG_TITLE);
            writer.writeCharacters(td.title);
            writer.writeEndElement();
            writer.writeStartElement(XmlModelReader.TAG_DETAILS);
            writer.writeCharacters(td.detail);
            writer.writeEndElement();

            writer.writeEndElement();
        }
    }

    private void getLeafXML(final AlarmTreeLeaf leaf) throws Exception
    {
        final String description = leaf.getDescription();
        if (description != null && !description.isEmpty())
        {
            writer.writeStartElement(XmlModelReader.TAG_DESCRIPTION);
            writer.writeCharacters(description);
            writer.writeEndElement();
        }

        final String enabled = leaf.isEnabled() ? "true" : "false";

        writer.writeStartElement(XmlModelReader.TAG_ENABLED);
        writer.writeCharacters(enabled);
        writer.writeEndElement();

        final String latching = leaf.isLatching() ? "true" : "false";

        writer.writeStartElement(XmlModelReader.TAG_LATCHING);
        writer.writeCharacters(latching);
        writer.writeEndElement();

        final String annunciating = leaf.isAnnunciating() ? "true" : "false";

        writer.writeStartElement(XmlModelReader.TAG_ANNUNCIATING);
        writer.writeCharacters(annunciating);
        writer.writeEndElement();

        final int delay = leaf.getDelay();

        // A delay less than zero doesn't make sense but is technically possible.
        if (delay != 0)
        {
            writer.writeStartElement(XmlModelReader.TAG_DELAY);
            writer.writeCharacters(Integer.toString(delay));
            writer.writeEndElement();
        }

        final int count = leaf.getCount();

        // Count is unsigned so can be assumed greater than 0.
        if (count > 0)
        {
            writer.writeStartElement(XmlModelReader.TAG_COUNT);
            writer.writeCharacters(Integer.toString(count));
            writer.writeEndElement();
        }

        final String filter = leaf.getFilter();
        if (filter != null && !filter.isEmpty())
        {
            writer.writeStartElement(XmlModelReader.TAG_FILTER);
            writer.writeCharacters(filter);
            writer.writeEndElement();
        }
    }

    public void close() throws IOException
    {
        try
        {
            // End and close document
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        }
        catch (final Exception ex)
        {
            throw new IOException("Failed to close XML", ex);
        }
    }
}
