/*******************************************************************************
 * Copyright (c) 2015-2017 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.csstudio.display.builder.representation.javafx.widgets;

import static org.csstudio.display.builder.representation.ToolkitRepresentation.logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.csstudio.display.builder.model.DirtyFlag;
import org.csstudio.display.builder.model.WidgetProperty;
import org.csstudio.display.builder.model.properties.FormatOption;
import org.csstudio.display.builder.model.util.FormatOptionHandler;
import org.csstudio.display.builder.model.util.VTypeUtil;
import org.csstudio.display.builder.model.widgets.RadioWidget;
import org.csstudio.display.builder.representation.javafx.JFXUtil;
import org.phoebus.ui.javafx.Styles;
import org.phoebus.vtype.VEnum;
import org.phoebus.vtype.VNumber;
import org.phoebus.vtype.VType;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** Creates JavaFX item for model widget
 *  @author Amanda Carpenter
 */
@SuppressWarnings("nls")
public class RadioRepresentation extends JFXBaseRepresentation<TilePane, RadioWidget>
{
    private volatile boolean active = false;
    private final ToggleGroup toggle = new ToggleGroup();

    private final DirtyFlag dirty_size = new DirtyFlag();
    private final DirtyFlag dirty_style = new DirtyFlag();
    private final DirtyFlag dirty_content = new DirtyFlag();
    private volatile List<String> items = Collections.emptyList();
    private volatile int index = -1;
    protected volatile boolean enabled = true;

    @Override
    public TilePane createJFXNode() throws Exception
    {
        final TilePane pane = new TilePane(5.0, 5.0, createRadioButton(null));
        pane.setTileAlignment(Pos.BASELINE_LEFT);
        return pane;
    }

    private RadioButton createRadioButton(final String text)
    {
        final RadioButton rb = new RadioButton(text);
        rb.setToggleGroup(toggle);
        rb.setMnemonicParsing(false);
        return rb;
    }

    @Override
    protected void registerListeners()
    {
        super.registerListeners();
        model_widget.propWidth().addUntypedPropertyListener(this::sizeChanged);
        model_widget.propHeight().addUntypedPropertyListener(this::sizeChanged);
        model_widget.propHorizontal().addUntypedPropertyListener(this::sizeChanged);

        model_widget.propForegroundColor().addUntypedPropertyListener(this::styleChanged);
        model_widget.propFont().addUntypedPropertyListener(this::styleChanged);
        model_widget.propEnabled().addUntypedPropertyListener(this::styleChanged);
        model_widget.runtimePropPVWritable().addUntypedPropertyListener(this::styleChanged);

        model_widget.runtimePropValue().addUntypedPropertyListener(this::contentChanged);
        model_widget.propItemsFromPV().addUntypedPropertyListener(this::contentChanged);
        model_widget.propItems().addUntypedPropertyListener(this::contentChanged);

        if (! toolkit.isEditMode())
            toggle.selectedToggleProperty().addListener(this::selectionChanged);

        // Initially populate pane with radio buttons
        contentChanged(null, null, null);
    }

    @Override
    protected boolean isFilteringEditModeClicks()
    {
        return true;
    }

    private void selectionChanged(final ObservableValue<? extends Toggle> obs, final Toggle oldval, final Toggle newval)
    {
        if (!active  &&  newval != null)
        {
            active = true;
            try
            {
                // For now reset to old value.
                // New value will be shown if the PV accepts it and sends a value update.
                toggle.selectToggle(oldval);

                if (enabled)
                {
                    final Object value;
                    final VType pv_value = model_widget.runtimePropValue().getValue();
                    if (pv_value instanceof VEnum  ||  pv_value instanceof VNumber)
                        // PV uses enumerated or numeric type, so write the index
                        value = toggle.getToggles().indexOf(newval);
                    else // PV uses text
                        value = FormatOptionHandler.parse(pv_value,
                                                          ((RadioButton) newval).getText(),
                                                          FormatOption.DEFAULT);
                    logger.log(Level.FINE, "Writing " + value);
                    toolkit.fireWrite(model_widget, value);
                }
            }
            finally
            {
                active = false;
            }
        }
    }

    private void styleChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_style.mark();
        toolkit.scheduleUpdate(this);
    }

    private void sizeChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        dirty_size.mark();
        toolkit.scheduleUpdate(this);
    }

    /** @param value Current value of PV
     *  @param fromPV Get list of items from PV (if possible)?
     *  @return List of radio button labels
     */
    private List<String> computeItems(final VType value, final boolean fromPV)
    {
        if (value instanceof VEnum  &&  fromPV)
            return ((VEnum)value).getLabels();

        final List<WidgetProperty<String>> itemProps = model_widget.propItems().getValue();
        final List<String> new_items = new ArrayList<>(itemProps.size());
        for (WidgetProperty<String> itemProp : itemProps)
            new_items.add(itemProp.getValue());
        return new_items;
    }

    private int determineIndex(final List<String> labels, final VType value)
    {
        if (value instanceof VEnum)
            return ((VEnum)value).getIndex();
        if (value instanceof VNumber)
            return ((VNumber)value).getValue().intValue();
        return labels.indexOf(VTypeUtil.getValueString(value, false));
    }

    /** The value or how we treat the value changed */
    private void contentChanged(final WidgetProperty<?> property, final Object old_value, final Object new_value)
    {
        final VType value = model_widget.runtimePropValue().getValue();
        final boolean fromPV = model_widget.propItemsFromPV().getValue() && value instanceof VEnum;
        items = computeItems(value, fromPV);
        index = determineIndex(items, value);
        dirty_content.mark();
        dirty_style.mark(); // Adjust colors
        toolkit.scheduleUpdate(this);
    }

    @Override
    public void updateChanges()
    {
        super.updateChanges();
        if (dirty_size.checkAndClear())
        {
            // Size
            jfx_node.setPrefSize(model_widget.propWidth().getValue(), model_widget.propHeight().getValue());
            // Orientation seems more of a hint. JFX will chose based on width vs. height.
            jfx_node.setOrientation(
                    model_widget.propHorizontal().getValue() ? Orientation.HORIZONTAL : Orientation.VERTICAL);
        }
        if (dirty_content.checkAndClear())
        {
            active = true;
            try
            {
                // Copy volatile lists before iteration
                final List<String> save_items = new ArrayList<>(items);
                final List<Node> save_buttons = new ArrayList<>(jfx_node.getChildren());

                // Set text of buttons, adding new ones as needed
                int i, save_index = index;
                for (i = 0; i < save_items.size(); i++)
                {
                    if (i < save_buttons.size())
                        ((RadioButton) save_buttons.get(i)).setText(save_items.get(i));
                    else
                        save_buttons.add(createRadioButton(save_items.get(i)));
                }
                while (i < save_buttons.size() && save_buttons.size() > 1)
                    save_buttons.remove(save_buttons.size() - 1);

                // Select one of the radio buttons
                toggle.selectToggle(save_index < 0 || save_index >= save_buttons.size()
                                    ? null
                                    : (Toggle) save_buttons.get(save_index));
                jfx_node.getChildren().setAll(save_buttons);
            }
            finally
            {
                active = false;
            }
        }
        if (dirty_style.checkAndClear())
        {
            final Color fg = JFXUtil.convert(model_widget.propForegroundColor().getValue());
            final Font font = JFXUtil.convert(model_widget.propFont().getValue());
            // Don't disable the widget, because that would also remove the
            // context menu etc.
            // Just apply a style that matches the disabled look.
            enabled = model_widget.propEnabled().getValue() &&
                      model_widget.runtimePropPVWritable().getValue();
            Styles.update(jfx_node, Styles.NOT_ENABLED, !enabled);
            for (Node rb_node : jfx_node.getChildren())
            {
                final RadioButton rb = (RadioButton) rb_node;
                rb.setTextFill(fg);
                rb.setFont(font);
            }
        }
    }
}
