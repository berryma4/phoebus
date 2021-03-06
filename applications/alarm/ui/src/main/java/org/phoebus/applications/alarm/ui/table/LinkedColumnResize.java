/*******************************************************************************
 * Copyright (c) 2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.phoebus.applications.alarm.ui.table;

import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableView.ResizeFeatures;
import javafx.util.Callback;

/** When column in 'master' table is resized, update column in 'other' table.
 *  @author Kay Kasemir
 */
@SuppressWarnings("rawtypes")
class LinkedColumnResize implements Callback<ResizeFeatures, Boolean>
{
    private static boolean updating = false;

    private final TableView<AlarmInfoRow> master, other;

    public LinkedColumnResize(final TableView<AlarmInfoRow> master, final TableView<AlarmInfoRow> other)
    {
        this.master = master;
        this.other = other;
    }

    @Override
    public Boolean call(final ResizeFeatures param)
    {
        final Boolean result = TableView.UNCONSTRAINED_RESIZE_POLICY.call(param);
        if (! updating)
        {
            updating = true;
            int i = 0;
            for (TableColumn col : master.getColumns())
                other.getColumns().get(i++).setPrefWidth(col.getWidth());
            updating = false;
        }
        return result;
    }
}