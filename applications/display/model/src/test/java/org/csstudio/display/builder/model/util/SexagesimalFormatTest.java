/*******************************************************************************
 * Copyright (c) 2015-2016 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.model.util;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.junit.Test;
import org.phoebus.vtype.Display;
import org.phoebus.vtype.ValueFactory;

/** JUnit test of {@link SexagesimalFormat}
 *  @author Kay Kasemir
 *  @author lcavalli provided original implementation for BOY, https://github.com/ControlSystemStudio/cs-studio/pull/1978
 */
@SuppressWarnings("nls")
public class SexagesimalFormatTest
{
    final NumberFormat fmt = DecimalFormat.getNumberInstance();
    final Display display = ValueFactory.newDisplay(-10.0, -9.0, -8.0, "V", fmt, 8.0, 9.0, 10.0, -10.0, 10.0);

    @Test
    public void testSexagesimal() throws Exception
    {
        double number = 12.5824414;
        String text = SexagesimalFormat.format(number, 7);
        double parsed = SexagesimalFormat.parse(text);
        System.out.println(number + " -> '" + text + "' -> " + parsed);
        assertThat(text, equalTo("12:34:56.789"));
        assertEquals(parsed, number, 0.0000001);

        number = -12.5824414;
        text = SexagesimalFormat.format(number, 7);
        parsed = SexagesimalFormat.parse(text);
        System.out.println(number + " -> '" + text + "' -> " + parsed);
        assertThat(text, equalTo("-12:34:56.789"));
        assertEquals(parsed, number, 0.0000001);

        number = 12.9999999;
        text = SexagesimalFormat.format(number, 7);
        parsed = SexagesimalFormat.parse(text);
        System.out.println(number + " -> '" + text + "' -> " + parsed);
        assertThat(text, equalTo("13:00:00.000"));
        assertEquals(parsed, number, 0.0000001);

        text = SexagesimalFormat.format(number, 8);
        parsed = SexagesimalFormat.parse(text);
        System.out.println(number + " -> '" + text + "' -> " + parsed);
        assertThat(text, equalTo("12:59:59.9996"));
        assertEquals(parsed, number, 0.0000001);

        text = "12:30";
        parsed = SexagesimalFormat.parse(text);
        System.out.println("'" + text + "' -> " + parsed);
        assertEquals(parsed, 12.5, 0.1);

        text = "12:30:01";
        parsed = SexagesimalFormat.parse(text);
        System.out.println("'" + text + "' -> " + parsed);
        assertEquals(parsed, 12.5 + 1.0/60/60, 0.000001);
    }
}
