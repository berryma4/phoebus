/*******************************************************************************
 * Copyright (c) 2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv.ca;

import java.util.List;

import org.phoebus.util.array.ArrayDouble;
import org.phoebus.util.array.ArrayInt;
import org.phoebus.util.array.ListDouble;
import org.phoebus.util.array.ListInt;
import org.phoebus.vtype.ArrayDimensionDisplay;
import org.phoebus.vtype.VDoubleArray;
import org.phoebus.vtype.VTypeToString;
import org.phoebus.vtype.ValueUtil;

import gov.aps.jca.dbr.DBR_TIME_Double;
import gov.aps.jca.dbr.GR;

/** Wrap DBR as VType
 *
 *  <p>Based on ideas from org.epics.pvmanager.jca, Gabriele Carcassi
 *  @author Kay Kasemir
 */
public class VTypeForDoubleArray extends DBRAlarmTimeDisplayWrapper<DBR_TIME_Double> implements VDoubleArray
{
    public VTypeForDoubleArray(final GR metadata, final DBR_TIME_Double dbr)
    {
        super(metadata, dbr);
    }

    @Override
    public List<ArrayDimensionDisplay> getDimensionDisplay()
    {
        return ValueUtil.defaultArrayDisplay(this);
    }

    @Override
    public ListInt getSizes()
    {
        return new ArrayInt(dbr.getDoubleValue().length);
    }

    @Override
    public ListDouble getData()
    {
        return new ArrayDouble(dbr.getDoubleValue());
    }

    @Override
    public String toString()
    {
        return VTypeToString.toString(this);
    }
}
